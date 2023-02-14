// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@State(name = "EntryPointsManager")
public abstract class EntryPointsManagerBase extends EntryPointsManager implements PersistentStateComponent<Element> {
  @ApiStatus.Internal
  public static final ExtensionPointName<EntryPoint> DEAD_CODE_EP_NAME = new ExtensionPointName<>("com.intellij.deadCode");

  @NonNls private static final String[] STANDARD_ANNOS = {
    "javax.ws.rs.*",
  };

  // null means uninitialized
  private volatile List<String> ADDITIONAL_ANNOS;

  public Collection<String> getAdditionalAnnotations() {
    List<String> annos = ADDITIONAL_ANNOS;
    if (annos == null) {
      annos = new ArrayList<>();
      Collections.addAll(annos, STANDARD_ANNOS);
      for (EntryPoint extension : DEAD_CODE_EP_NAME.getExtensionList()) {
        final String[] ignoredAnnotations = extension.getIgnoreAnnotations();
        if (ignoredAnnotations != null) {
          ContainerUtil.addAll(annos, ignoredAnnotations);
        }
      }
      ADDITIONAL_ANNOS = annos = Collections.unmodifiableList(annos);
    }
    return annos;
  }
  public JDOMExternalizableStringList ADDITIONAL_ANNOTATIONS = new JDOMExternalizableStringList();
  protected List<String> myWriteAnnotations = new ArrayList<>();
  private final Map<String, SmartRefElementPointer> myPersistentEntryPoints;
  private final LinkedHashSet<ClassPattern> myPatterns = new LinkedHashSet<>();
  private final Set<RefElement> myTemporaryEntryPoints;
  private static final String VERSION = "2.0";
  @NonNls private static final String VERSION_ATTR = "version";
  @NonNls private static final String ENTRY_POINT_ATTR = "entry_point";
  private boolean myAddNonJavaEntries = true;
  private boolean myResolved;
  protected final Project myProject;
  private long myLastModificationCount = -1;

  public EntryPointsManagerBase(@NotNull Project project) {
    myProject = project;
    myTemporaryEntryPoints = Collections.synchronizedSet(new HashSet<>());
    myPersistentEntryPoints = new LinkedHashMap<>(); // To keep the order between readExternal to writeExternal
    DEAD_CODE_EP_NAME.addChangeListener(() -> {
      if (ADDITIONAL_ANNOS != null) {
        ADDITIONAL_ANNOS = null;
      }
      DaemonCodeAnalyzer.getInstance(project).restart();
    }, this);
  }

  public static EntryPointsManagerBase getInstance(Project project) {
    return (EntryPointsManagerBase)project.getService(EntryPointsManager.class);
  }

  @Override
  public void loadState(@NotNull Element element) {
    Element entryPointsElement = element.getChild("entry_points");
    if (entryPointsElement != null) {
      final String version = entryPointsElement.getAttributeValue(VERSION_ATTR);
      if (!Comparing.strEqual(version, VERSION)) {
        convert(entryPointsElement, myPersistentEntryPoints);
      }
      else {
        List<Element> content = entryPointsElement.getChildren();
        for (final Element entryElement : content) {
          if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
            SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(entryElement);
            myPersistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
          }
        }
      }
    }
    try {
      ADDITIONAL_ANNOTATIONS.readExternal(element);
    }
    catch (Throwable ignored) {
    }

    getPatterns().clear();
    for (Element pattern : element.getChildren("pattern")) {
      final ClassPattern classPattern = new ClassPattern();
      XmlSerializer.deserializeInto(pattern, classPattern);
      getPatterns().add(classPattern);
    }

    myWriteAnnotations.clear();
    final Element writeAnnotations = element.getChild("writeAnnotations");
    if (writeAnnotations != null) {
      for (Element annoElement : writeAnnotations.getChildren("writeAnnotation")) {
        final String value = annoElement.getAttributeValue("name");
        if (value != null) {
          myWriteAnnotations.add(value);
        }
      }
    }
  }

  @Override
  public Element getState()  {
    Element element = new Element("state");
    writeExternal(element, myPersistentEntryPoints, ADDITIONAL_ANNOTATIONS);
    if (!getPatterns().isEmpty()) {
      for (ClassPattern pattern : getPatterns()) {
        element.addContent(XmlSerializer.serialize(pattern));
      }
    }

    if (!myWriteAnnotations.isEmpty()) {
      final Element writeAnnotations = new Element("writeAnnotations");
      for (String writeAnnotation : myWriteAnnotations) {
        writeAnnotations.addContent(new Element("writeAnnotation").setAttribute("name", writeAnnotation));
      }
      element.addContent(writeAnnotations);
    }
    return element;
  }

  public static void writeExternal(final Element element,
                                   final Map<String, SmartRefElementPointer> persistentEntryPoints,
                                   final JDOMExternalizableStringList additional_annotations) {
    Collection<SmartRefElementPointer> elementPointers = persistentEntryPoints.values();
    if (!elementPointers.isEmpty()) {
      Element entryPointsElement = new Element("entry_points");
      entryPointsElement.setAttribute(VERSION_ATTR, VERSION);
      for (SmartRefElementPointer entryPoint : elementPointers) {
        assert entryPoint.isPersistent();
        entryPoint.writeExternal(entryPointsElement);
      }
      element.addContent(entryPointsElement);
    }

    if (!additional_annotations.isEmpty()) {
      additional_annotations.writeExternal(element);
    }
  }

  @Override
  public void resolveEntryPoints(@NotNull final RefManager manager) {
    if (!myResolved) {
      myResolved = true;
      cleanup();
      validateEntryPoints();

      ReadAction.run(() -> {
        for (SmartRefElementPointer entryPoint : myPersistentEntryPoints.values()) {
          if (entryPoint.resolve(manager)) {
            RefEntity refElement = entryPoint.getRefElement();
            ((RefElementImpl)refElement).setEntry(true);
            ((RefElementImpl)refElement).setPermanentEntry(entryPoint.isPersistent());
          }
        }

        getPatternEntryPoints(manager).forEach((entity) -> {
          entity.setEntry(true);
          entity.setPermanentEntry(true);
        });
      });
    }
  }

  private void purgeTemporaryEntryPoints() {
    synchronized (myTemporaryEntryPoints) {
      for (RefElement entryPoint : myTemporaryEntryPoints) {
        ((RefElementImpl)entryPoint).setEntry(false);
      }
    }

    myTemporaryEntryPoints.clear();
  }

  private List<RefElementImpl> getPatternEntryPoints(RefManager manager) {
    List<RefElementImpl> entries = new ArrayList<>();
    for (ClassPattern pattern : myPatterns) {
      final RefEntity refClass = ReadAction.compute(() -> manager.getReference(RefJavaManager.CLASS, pattern.pattern));
      if (refClass != null) {
        if (pattern.method.isEmpty()) {
          for (RefMethod refMethod : ((RefClass)refClass).getConstructors()) {
            entries.add((RefElementImpl)refMethod);
          }
        }
        else {
          List<RefEntity> children = refClass.getChildren();
          for (RefEntity entity : children) {
            if (entity instanceof RefMethodImpl && entity.getName().startsWith(pattern.method + "(")) {
              entries.add((RefElementImpl)entity);
            }
          }
        }
      }
    }
    return entries;
  }

  @Override
  public void addEntryPoint(@NotNull RefElement newEntryPoint, boolean isPersistent) {
    if (!newEntryPoint.isValid()) return;
    if (isPersistent) {
      if (newEntryPoint instanceof RefClass || newEntryPoint instanceof RefMethod) {
        RefClass refClass = newEntryPoint instanceof RefMethod ? ((RefMethod)newEntryPoint).getOwnerClass()
                                                               : (RefClass)newEntryPoint;
        if (refClass != null && !refClass.isAnonymous()) {
          final ClassPattern classPattern = new ClassPattern();
          classPattern.pattern = new SmartRefElementPointerImpl(refClass, true).getFQName();
          if (newEntryPoint instanceof RefMethod && !(newEntryPoint instanceof RefImplicitConstructor)) {
            classPattern.method = getMethodName(newEntryPoint);
          }
          getPatterns().add(classPattern);

          final EntryPointsManager entryPointsManager = getInstance(newEntryPoint.getRefManager().getProject());
          if (this != entryPointsManager) {
            entryPointsManager.addEntryPoint(newEntryPoint, true);
          }

          return;
        }
      }
    }

    if (newEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass)newEntryPoint;

      if (refClass.isAnonymous()) {
        // Anonymous class cannot be an entry point.
        return;
      }

      List<RefMethod> refConstructors = refClass.getConstructors();
      if (refConstructors.size() == 1) {
        addEntryPoint(refConstructors.get(0), isPersistent);
      }
      else if (refConstructors.size() > 1) {
        // Many constructors here. Need to ask user which ones are used
        for (RefMethod refConstructor : refConstructors) {
          addEntryPoint(refConstructor, isPersistent);
        }
      }
    }

    if (!isPersistent) {
      myTemporaryEntryPoints.add(newEntryPoint);
      ((RefElementImpl)newEntryPoint).setEntry(true);
    }
    else {
      if (myPersistentEntryPoints.get(newEntryPoint.getExternalName()) == null) {
        final SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, true);
        myPersistentEntryPoints.put(entry.getFQName(), entry);
        ((RefElementImpl)newEntryPoint).setEntry(true);
        ((RefElementImpl)newEntryPoint).setPermanentEntry(true);
        if (entry.isPersistent()) { //do save entry points
          final EntryPointsManager entryPointsManager = getInstance(newEntryPoint.getRefManager().getProject());
          if (this != entryPointsManager) {
            entryPointsManager.addEntryPoint(newEntryPoint, true);
          }
        }
      }
    }
  }

  private static String getMethodName(@NotNull RefElement newEntryPoint) {
    String methodSignature = newEntryPoint.getName();
    int indexOf = methodSignature.indexOf("(");
    return indexOf > 0 ? methodSignature.substring(0, indexOf) : methodSignature;
  }

  @Override
  public void removeEntryPoint(@NotNull RefElement anEntryPoint) {
    myTemporaryEntryPoints.remove(anEntryPoint);

    Set<Map.Entry<String, SmartRefElementPointer>> set = myPersistentEntryPoints.entrySet();
    String key = null;
    for (Map.Entry<String, SmartRefElementPointer> entry : set) {
      SmartRefElementPointer value = entry.getValue();
      if (value.getRefElement() == anEntryPoint) {
        key = entry.getKey();
        break;
      }
    }

    if (key != null) {
      myPersistentEntryPoints.remove(key);
    }
    ((RefElementImpl)anEntryPoint).setEntry(false);

    if (anEntryPoint.isPermanentEntry() && anEntryPoint.isValid()) {
      final Project project = anEntryPoint.getPsiElement().getProject();
      final EntryPointsManager entryPointsManager = getInstance(project);
      if (this != entryPointsManager) {
        entryPointsManager.removeEntryPoint(anEntryPoint);
      }
    }

    if (anEntryPoint instanceof RefMethod || anEntryPoint instanceof RefClass) {
      final RefClass aClass = anEntryPoint instanceof RefClass ? (RefClass)anEntryPoint : ((RefMethod)anEntryPoint).getOwnerClass();
      if (aClass != null) {
        final String qualifiedName = aClass.getQualifiedName();
        for (Iterator<ClassPattern> iterator = getPatterns().iterator(); iterator.hasNext(); ) {
          ClassPattern classPattern = iterator.next();
          if (Objects.equals(classPattern.pattern, qualifiedName)) {
            if (anEntryPoint instanceof RefMethod && ((RefMethod)anEntryPoint).isConstructor() || anEntryPoint instanceof RefClass) {
              if (classPattern.method.isEmpty()) {
                //todo if inheritance or pattern?
                iterator.remove();
              }
            }
            else {
              String methodName = getMethodName(anEntryPoint);
              if (methodName.equals(classPattern.method)) {
                iterator.remove();
              }
            }
          }
        }
      }
    }
  }

  @Override
  public RefElement @NotNull [] getEntryPoints(RefManager refManager) {
    validateEntryPoints();
    List<RefElement> entries = new ArrayList<>();
    Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
    for (SmartRefElementPointer refElementPointer : collection) {
      final RefEntity elt = refElementPointer.getRefElement();
      if (elt instanceof RefElement) {
        entries.add((RefElement)elt);
      }
    }
    entries.addAll(myTemporaryEntryPoints);

    entries.addAll(getPatternEntryPoints(refManager));

    return entries.toArray(new RefElement[0]);
  }

  @Override
  public void dispose() {
    cleanup();
  }

  private void validateEntryPoints() {
    long count = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    if (count != myLastModificationCount) {
      myLastModificationCount = count;
      Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
      SmartRefElementPointer[] entries = collection.toArray(new SmartRefElementPointer[0]);
      for (SmartRefElementPointer entry : entries) {
        RefElement refElement = (RefElement)entry.getRefElement();
        if (refElement != null && !refElement.isValid()) {
          myPersistentEntryPoints.remove(entry.getFQName());
        }
      }

      synchronized (myTemporaryEntryPoints) {
        final Iterator<RefElement> it = myTemporaryEntryPoints.iterator();
        while (it.hasNext()) {
          RefElement refElement = it.next();
          if (!refElement.isValid()) {
            it.remove();
          }
        }
      }
    }
  }

  @Override
  public void cleanup() {
    purgeTemporaryEntryPoints();
    Collection<SmartRefElementPointer> entries = myPersistentEntryPoints.values();
    for (SmartRefElementPointer entry : entries) {
      entry.freeReference();
    }
  }

  @Override
  public boolean isAddNonJavaEntries() {
    return myAddNonJavaEntries;
  }

  public void addAllPersistentEntries(@NotNull EntryPointsManagerBase manager) {
    myPersistentEntryPoints.putAll(manager.myPersistentEntryPoints);
    myPatterns.addAll(manager.getPatterns());
  }

  static void convert(@NotNull Element element, final Map<? super String, ? super SmartRefElementPointer> persistentEntryPoints) {
    List<Element> content = element.getChildren();
    for (final Element entryElement : content) {
      if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
        String fqName = entryElement.getAttributeValue(SmartRefElementPointerImpl.FQNAME_ATTR);
        final String type = entryElement.getAttributeValue(SmartRefElementPointerImpl.TYPE_ATTR);
        if (Comparing.strEqual(type, RefJavaManager.METHOD)) {

          int spaceIdx = fqName.indexOf(' ');
          int lastDotIdx = fqName.lastIndexOf('.');

          int parenIndex = fqName.indexOf('(');

          while (lastDotIdx > parenIndex) lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);

          boolean notype = false;
          if (spaceIdx < 0 || spaceIdx + 1 > lastDotIdx) {
            notype = true;
          }

          final String className = fqName.substring(notype ? 0 : spaceIdx + 1, lastDotIdx);
          final String methodSignature =
              notype ? fqName.substring(lastDotIdx + 1) : fqName.substring(0, spaceIdx) + ' ' + fqName.substring(lastDotIdx + 1);

          fqName = className + " " + methodSignature;
        }
        else if (Comparing.strEqual(type, RefJavaManager.FIELD)) {
          final int lastDotIdx = fqName.lastIndexOf('.');
          if (lastDotIdx > 0 && lastDotIdx < fqName.length() - 2) {
            String className = fqName.substring(0, lastDotIdx);
            String fieldName = fqName.substring(lastDotIdx + 1);
            fqName = className + " " + fieldName;
          }
          else {
            continue;
          }
        }
        SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(type, fqName);
        persistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
      }
    }
  }

  public void setAddNonJavaEntries(final boolean addNonJavaEntries) {
    myAddNonJavaEntries = addNonJavaEntries;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return element instanceof PsiField && AnnotationUtil.isAnnotated((PsiModifierListOwner)element, myWriteAnnotations, 0);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement element) {
    if (!(element instanceof PsiModifierListOwner owner)) return false;
    if (!ADDITIONAL_ANNOTATIONS.isEmpty() && ADDITIONAL_ANNOTATIONS.contains(Deprecated.class.getName()) &&
        element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)element).isDeprecated()) {
      return true;
    }

    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)element).getQualifiedName();
      if (qualifiedName != null) {
        for (ClassPattern pattern : getPatterns()) {
          if (pattern.method.isEmpty() && isAcceptedByPattern((PsiClass)element, qualifiedName, pattern, new HashSet<>())) {
            return true;
          }
        }
      }
    }

    if (element instanceof PsiMethod) {
      final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        final String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null) {
          final String name = ((PsiMethod)element).getName();
          for (ClassPattern pattern : getPatterns()) {
            if (pattern.method.isEmpty()) continue;
            boolean acceptedName = name.equals(pattern.method);
            if (!acceptedName) {
              final Pattern methodRegexp = pattern.getMethodRegexp();
              acceptedName = methodRegexp != null && methodRegexp.matcher(name).matches();
            }
            if (acceptedName && isAcceptedByPattern(containingClass, qualifiedName, pattern, new HashSet<>())) {
              return true;
            }
          }
        }
      }
    }
    final Collection<String> defaultAdditionalAnnotations = getAdditionalAnnotations();
    return AnnotationUtil.checkAnnotatedUsingPatterns(owner, ADDITIONAL_ANNOTATIONS) ||
           AnnotationUtil.checkAnnotatedUsingPatterns(owner, defaultAdditionalAnnotations) ||
           MetaAnnotationUtil.isMetaAnnotated(owner, ADDITIONAL_ANNOTATIONS) ||
           MetaAnnotationUtil.isMetaAnnotated(owner, defaultAdditionalAnnotations);
  }

  private static boolean isAcceptedByPattern(@NotNull PsiClass element, String qualifiedName, ClassPattern pattern, Set<? super PsiClass> visited) {
    if (qualifiedName == null) {
      return false;
    }

    if (qualifiedName.equals(pattern.pattern)) {
      return true;
    }

    final Pattern regexp = pattern.getRegexp();
    if (regexp != null) {
      try {
        if (regexp.matcher(qualifiedName).matches()) {
          return true;
        }
      }
      catch (PatternSyntaxException ignored) {}
    }

    if (pattern.hierarchically) {
      for (PsiClass superClass : element.getSupers()) {
        final String superClassQualifiedName = superClass.getQualifiedName();
        if (visited.add(superClass) && isAcceptedByPattern(superClass, superClassQualifiedName, pattern, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  public List<String> getCustomAdditionalAnnotations() {
    return List.copyOf(ADDITIONAL_ANNOTATIONS);
  }

  public List<String> getWriteAnnotations() {
    return List.copyOf(myWriteAnnotations);
  }

  public LinkedHashSet<ClassPattern> getPatterns() {
    return myPatterns;
  }

  @Tag("pattern")
  public static class ClassPattern {
    @Attribute("value")
    public @NlsSafe String pattern = "";
    @Attribute("hierarchically")
    public boolean hierarchically = false;

    @Attribute("method")
    public String method = "";


    private Pattern regexp;
    private Pattern methodRegexp;

    public ClassPattern(@NotNull ClassPattern classPattern) {
      hierarchically = classPattern.hierarchically;
      pattern = classPattern.pattern;
      method = classPattern.method;
    }

    public ClassPattern() {}

    @Nullable
    public Pattern getRegexp() {
      if (regexp == null && pattern.contains("*")) {
        regexp = createRegexp(pattern);
      }
      return regexp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassPattern pattern1 = (ClassPattern)o;

      if (hierarchically != pattern1.hierarchically) return false;
      if (!pattern.equals(pattern1.pattern)) return false;
      if (!method.equals(pattern1.method)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = pattern.hashCode();
      result = 31 * result + (hierarchically ? 1 : 0);
      result = 31 * result + method.hashCode();
      return result;
    }

    public Pattern getMethodRegexp() {
      if (methodRegexp == null && method.contains("*")) {
        methodRegexp = createRegexp(method);
      }
      return methodRegexp;
    }

    private static Pattern createRegexp(final String pattern) {
      final String replace = pattern.replace(".", "\\.").replace("*", ".*");
      try {
        return Pattern.compile(replace);
      }
      catch (PatternSyntaxException e) {
        return null;
      }
    }
  }

  public class AddImplicitlyWriteAnnotation implements IntentionAction, LocalQuickFix {
    private final String myQualifiedName;

    public AddImplicitlyWriteAnnotation(String qualifiedName) {myQualifiedName = qualifiedName;}

    @Override
    @NotNull
    public String getText() {
      return QuickFixBundle.message("fix.add.write.annotation.text",  myQualifiedName);
    }

    @Override
    public @NotNull String getName() {
      return getText();
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return QuickFixBundle.message("fix.unused.symbol.injection.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      performAction(descriptor.getStartElement().getContainingFile());
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      return new IntentionPreviewInfo.Html(QuickFixBundle.message("fix.add.write.annotation.description", myQualifiedName));
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return new IntentionPreviewInfo.Html(QuickFixBundle.message("fix.add.write.annotation.description", myQualifiedName));
    }

    @Override
    public boolean isAvailable(@NotNull Project project1, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      performAction(file);
    }

    private void performAction(@NotNull PsiFile file) {
      Project project = file.getProject();
      VirtualFile vFile = file.getVirtualFile();
      doAddAnnotation(project);
      UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
        @Override
        public void undo() {
          if (myWriteAnnotations.removeAll(List.of(myQualifiedName))) {
            ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
          }
        }

        @Override
        public void redo() {
          doAddAnnotation(project);
        }
      });
    }

    private void doAddAnnotation(Project project) {
      if (!myWriteAnnotations.contains(myQualifiedName)) {
        myWriteAnnotations.add(myQualifiedName);
        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
