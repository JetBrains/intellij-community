// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
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
public abstract class EntryPointsManagerBase extends EntryPointsManager implements PersistentStateComponent<Element>,
                                                                                   OptionContainer {
  @ApiStatus.Internal
  public static final ExtensionPointName<EntryPoint> DEAD_CODE_EP_NAME = new ExtensionPointName<>("com.intellij.deadCode");

  private static final @NonNls String[] STANDARD_ANNOS = {
    "javax.ws.rs.*",
  };

  // null means uninitialized
  private volatile List<String> ADDITIONAL_ANNOS;

  public @NotNull Collection<String> getAdditionalAnnotations() {
    List<String> annos = ADDITIONAL_ANNOS;
    if (annos == null) {
      annos = new ArrayList<>();
      Collections.addAll(annos, STANDARD_ANNOS);
      for (EntryPoint extension : DEAD_CODE_EP_NAME.getExtensionList()) {
        String[] ignoredAnnotations = extension.getIgnoreAnnotations();
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
  private final Map<String, SmartRefElementPointer> myPersistentEntryPoints = new LinkedHashMap<>();
  private final Set<ClassPattern> myPatterns = new LinkedHashSet<>(); // To keep the order between readExternal to writeExternal
  private final Set<RefElement> myTemporaryEntryPoints = Collections.synchronizedSet(new HashSet<>());
  private static final String VERSION = "2.0";
  private static final @NonNls String VERSION_ATTR = "version";
  private static final @NonNls String ENTRY_POINT_ATTR = "entry_point";
  private boolean myAddNonJavaEntries = true;
  private boolean myResolved;
  protected final Project myProject;
  private long myLastModificationCount = -1;

  public EntryPointsManagerBase(@NotNull Project project) {
    myProject = project;
    DEAD_CODE_EP_NAME.addChangeListener(() -> {
      if (ADDITIONAL_ANNOS != null) {
        ADDITIONAL_ANNOS = null;
      }
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("EntryPointsManagerBase.DEAD_CODE_EP_NAME changed");
    }, this);
  }

  public static EntryPointsManagerBase getInstance(Project project) {
    return (EntryPointsManagerBase)EntryPointsManager.getInstance(project);
  }

  @Override
  public void loadState(@NotNull Element element) {
    Element entryPointsElement = element.getChild("entry_points");
    if (entryPointsElement != null) {
      String version = entryPointsElement.getAttributeValue(VERSION_ATTR);
      if (!Comparing.strEqual(version, VERSION)) {
        convert(entryPointsElement, myPersistentEntryPoints);
      }
      else {
        List<Element> content = entryPointsElement.getChildren();
        for (Element entryElement : content) {
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
      ClassPattern classPattern = new ClassPattern();
      XmlSerializer.deserializeInto(pattern, classPattern);
      getPatterns().add(classPattern);
    }

    myWriteAnnotations.clear();
    Element writeAnnotations = element.getChild("writeAnnotations");
    if (writeAnnotations != null) {
      for (Element annoElement : writeAnnotations.getChildren("writeAnnotation")) {
        String value = annoElement.getAttributeValue("name");
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
      Element writeAnnotations = new Element("writeAnnotations");
      for (String writeAnnotation : myWriteAnnotations) {
        writeAnnotations.addContent(new Element("writeAnnotation").setAttribute("name", writeAnnotation));
      }
      element.addContent(writeAnnotations);
    }
    return element;
  }

  public static void writeExternal(@NotNull Element element,
                                   @NotNull Map<String, SmartRefElementPointer> persistentEntryPoints,
                                   @NotNull JDOMExternalizableStringList additional_annotations) {
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
  public void resolveEntryPoints(@NotNull RefManager manager) {
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

  private @NotNull List<RefElementImpl> getPatternEntryPoints(@NotNull RefManager manager) {
    List<RefElementImpl> entries = new ArrayList<>();
    for (ClassPattern pattern : myPatterns) {
      RefEntity refClass = ReadAction.compute(() -> manager.getReference(RefJavaManager.CLASS, pattern.pattern));
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
          ClassPattern classPattern = new ClassPattern();
          classPattern.pattern = new SmartRefElementPointerImpl(refClass, true).getFQName();
          if (newEntryPoint instanceof RefMethod && !(newEntryPoint instanceof RefImplicitConstructor)) {
            classPattern.method = getMethodName(newEntryPoint);
          }
          getPatterns().add(classPattern);

          EntryPointsManager entryPointsManager = getInstance(newEntryPoint.getRefManager().getProject());
          if (this != entryPointsManager) {
            entryPointsManager.addEntryPoint(newEntryPoint, true);
          }

          return;
        }
      }
    }

    if (newEntryPoint instanceof RefClass refClass) {
      if (refClass.isAnonymous()) {
        // Anonymous class cannot be an entry point.
        return;
      }

      if (isPersistent) {
        for (RefMethod refConstructor : refClass.getConstructors()) {
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
        SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, true);
        myPersistentEntryPoints.put(entry.getFQName(), entry);
        ((RefElementImpl)newEntryPoint).setEntry(true);
        ((RefElementImpl)newEntryPoint).setPermanentEntry(true);
        if (entry.isPersistent()) { //do save entry points
          EntryPointsManager entryPointsManager = getInstance(newEntryPoint.getRefManager().getProject());
          if (this != entryPointsManager) {
            entryPointsManager.addEntryPoint(newEntryPoint, true);
          }
        }
      }
    }
  }

  private static @NotNull String getMethodName(@NotNull RefElement newEntryPoint) {
    String methodSignature = newEntryPoint.getName();
    int indexOf = methodSignature.indexOf('(');
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
      Project project = anEntryPoint.getPsiElement().getProject();
      EntryPointsManager entryPointsManager = getInstance(project);
      if (this != entryPointsManager) {
        entryPointsManager.removeEntryPoint(anEntryPoint);
      }
    }

    if (anEntryPoint instanceof RefMethod || anEntryPoint instanceof RefClass) {
      RefClass aClass = anEntryPoint instanceof RefClass ? (RefClass)anEntryPoint : ((RefMethod)anEntryPoint).getOwnerClass();
      if (aClass != null) {
        String qualifiedName = aClass.getQualifiedName();
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
  public RefElement @NotNull [] getEntryPoints(@NotNull RefManager refManager) {
    validateEntryPoints();
    List<RefElement> entries = new ArrayList<>();
    Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
    for (SmartRefElementPointer refElementPointer : collection) {
      RefEntity elt = refElementPointer.getRefElement();
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
        myTemporaryEntryPoints.removeIf(refElement -> !refElement.isValid());
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

  @ApiStatus.Internal
  public static void convert(@NotNull Element element, @NotNull Map<? super String, ? super SmartRefElementPointer> persistentEntryPoints) {
    List<Element> content = element.getChildren();
    for (Element entryElement : content) {
      if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
        String fqName = entryElement.getAttributeValue(SmartRefElementPointerImpl.FQNAME_ATTR);
        String type = entryElement.getAttributeValue(SmartRefElementPointerImpl.TYPE_ATTR);
        if (Comparing.strEqual(type, RefJavaManager.METHOD)) {

          int spaceIdx = fqName.indexOf(' ');
          int lastDotIdx = fqName.lastIndexOf('.');

          int parenIndex = fqName.indexOf('(');

          while (lastDotIdx > parenIndex) lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);

          boolean noType = spaceIdx < 0 || spaceIdx + 1 > lastDotIdx;

          String className = fqName.substring(noType ? 0 : spaceIdx + 1, lastDotIdx);
          String methodSignature =
              noType ? fqName.substring(lastDotIdx + 1) : fqName.substring(0, spaceIdx) + ' ' + fqName.substring(lastDotIdx + 1);

          fqName = className + " " + methodSignature;
        }
        else if (Comparing.strEqual(type, RefJavaManager.FIELD)) {
          int lastDotIdx = fqName.lastIndexOf('.');
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

  public void setAddNonJavaEntries(boolean addNonJavaEntries) {
    myAddNonJavaEntries = addNonJavaEntries;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
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
      String qualifiedName = ((PsiClass)element).getQualifiedName();
      if (qualifiedName != null) {
        for (ClassPattern pattern : getPatterns()) {
          if (pattern.method.isEmpty() && isAcceptedByPattern((PsiClass)element, qualifiedName, pattern, new HashSet<>())) {
            return true;
          }
        }
      }
    }

    if (element instanceof PsiMethod) {
      PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null) {
          String name = ((PsiMethod)element).getName();
          for (ClassPattern pattern : getPatterns()) {
            if (pattern.method.isEmpty()) continue;
            boolean acceptedName = name.equals(pattern.method);
            if (!acceptedName) {
              Pattern methodRegexp = pattern.getMethodRegexp();
              acceptedName = methodRegexp != null && methodRegexp.matcher(name).matches();
            }
            if (acceptedName && isAcceptedByPattern(containingClass, qualifiedName, pattern, new HashSet<>())) {
              return true;
            }
          }
        }
      }
    }
    Collection<String> defaultAdditionalAnnotations = getAdditionalAnnotations();
    return AnnotationUtil.checkAnnotatedUsingPatterns(owner, ADDITIONAL_ANNOTATIONS) ||
           AnnotationUtil.checkAnnotatedUsingPatterns(owner, defaultAdditionalAnnotations) ||
           MetaAnnotationUtil.isMetaAnnotated(owner, ADDITIONAL_ANNOTATIONS) ||
           MetaAnnotationUtil.isMetaAnnotated(owner, defaultAdditionalAnnotations);
  }

  private static boolean isAcceptedByPattern(@NotNull PsiClass element, @NotNull String qualifiedName, @NotNull ClassPattern pattern, @NotNull Set<? super PsiClass> visited) {
    if (qualifiedName.equals(pattern.pattern)) {
      return true;
    }
    Pattern regexp = pattern.getRegexp();
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
        String superClassQualifiedName = superClass.getQualifiedName();
        if (visited.add(superClass) && superClassQualifiedName != null && isAcceptedByPattern(superClass, superClassQualifiedName, pattern, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  public @NotNull List<String> getCustomAdditionalAnnotations() {
    return List.copyOf(ADDITIONAL_ANNOTATIONS);
  }

  public @NotNull List<String> getWriteAnnotations() {
    return List.copyOf(myWriteAnnotations);
  }

  public @NotNull Set<ClassPattern> getPatterns() {
    return myPatterns;
  }

  @Tag("pattern")
  public static class ClassPattern {
    @Attribute("value")
    public @NlsSafe String pattern = "";
    @Attribute("hierarchically")
    public boolean hierarchically;

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

    public @Nullable Pattern getRegexp() {
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

    Pattern getMethodRegexp() {
      if (methodRegexp == null && method.contains("*")) {
        methodRegexp = createRegexp(method);
      }
      return methodRegexp;
    }

    private static Pattern createRegexp(@NotNull String pattern) {
      String replace = pattern.replace(".", "\\.").replace("*", ".*");
      try {
        return Pattern.compile(replace);
      }
      catch (PatternSyntaxException e) {
        return null;
      }
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.stringList("myWriteAnnotations", JavaBundle.message("separator.mark.field.as.implicitly.written.if.annotated.by"),
                         new JavaClassValidator().annotationsOnly()),
      OptPane.stringList("ADDITIONAL_ANNOTATIONS", JavaBundle.message("separator.mark.as.entry.point.if.annotated.by"),
                         new JavaClassValidator().annotationsOnly()));
  }

  private static class AddAnnotation implements ModCommandAction {
    private final @NotNull String myQualifiedName;
    private final @NotNull String myBindId;
    private final @NotNull @IntentionName String myMessage;

    private AddAnnotation(@NotNull String qualifiedName, @NotNull String bindId, @NotNull @IntentionName String message) { 
      myQualifiedName = qualifiedName; 
      myBindId = bindId;
      myMessage = message;
    }

    @Override
    public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
      return Presentation.of(myMessage);
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("fix.unused.symbol.injection.family");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      return ModCommand.updateOptionList(context.file(), myBindId, list -> {
        list.add(myQualifiedName);
        list.sort(null);
      });
    }
  }

  /**
   * @param qualifiedName annotation qualified name
   * @return an action that adds the specified annotation qualified name to the list of implicitly written fields annotations
   */
  public static @NotNull ModCommandAction createAddImplicitWriteAnnotation(@NotNull String qualifiedName) {
    return new AddAnnotation(qualifiedName, "EntryPointsManager.myWriteAnnotations",
                             QuickFixBundle.message("fix.add.write.annotation.text", qualifiedName));
  }

  /**
   * @param qualifiedName annotation qualified name
   * @return an action that adds the specified annotation qualified name to the list of entry point annotations
   */
  public static @NotNull ModCommandAction createAddEntryPointAnnotation(@NotNull String qualifiedName) {
    return new AddAnnotation(qualifiedName, "EntryPointsManager.ADDITIONAL_ANNOTATIONS",
                             QuickFixBundle.message("fix.unused.symbol.injection.text", qualifiedName));
  }

  /**
   * Provides bindId = "EntryPointsManager.myWriteAnnotations" and "EntryPointsManager.ADDITIONAL_ANNOTATIONS"
   * lists to control entry points
   */
  public static final class Provider implements OptionControllerProvider {
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      Project project = context.getProject();
      return getInstance(project).getOptionController()
        .onValueSet((bindId, value) -> ProjectInspectionProfileManager.getInstance(project).fireProfileChanged());
    }

    @Override
    public @NotNull String name() {
      return "EntryPointsManager";
    }
  }
}
