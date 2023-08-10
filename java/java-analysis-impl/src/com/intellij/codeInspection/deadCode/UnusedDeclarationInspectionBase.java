// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.Stack;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.uast.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UnusedDeclarationInspectionBase extends GlobalInspectionTool {
  protected static final Logger LOG = Logger.getInstance(UnusedDeclarationInspectionBase.class);

  public boolean ADD_MAINS_TO_ENTRIES = true;
  public boolean ADD_APPLET_TO_ENTRIES = true;
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  public boolean ADD_NONJAVA_TO_ENTRIES = true;
  protected boolean TEST_ENTRY_POINTS = true;

  public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  public static final String ALTERNATIVE_ID = "UnusedDeclaration";

  final UnusedSymbolLocalInspectionBase myLocalInspectionBase = createUnusedSymbolLocalInspection();

  private static final Key<Set<RefElement>> PROCESSED_SUSPICIOUS_ELEMENTS_KEY = Key.create("java.unused.declaration.processed.suspicious.elements");
  private static final Key<Integer> PHASE_KEY = Key.create("java.unused.declaration.phase");

  private final boolean myEnabledInEditor;

  /**
   * We can't have a direct link on the entry points as it blocks dynamic unloading of the plugins e.g. TestNG
   */
  private final Map<String, Element> entryPointElements = new ConcurrentHashMap<>();

  @SuppressWarnings("TestOnlyProblems")
  public UnusedDeclarationInspectionBase() {
    this(!ApplicationManager.getApplication().isUnitTestMode());
  }

  @TestOnly
  public UnusedDeclarationInspectionBase(boolean enabledInEditor) {
    myEnabledInEditor = enabledInEditor;
  }

  protected UnusedSymbolLocalInspectionBase createUnusedSymbolLocalInspection() {
    return new UnusedSymbolLocalInspectionBase();
  }

  @NotNull
  @Override
  public UnusedSymbolLocalInspectionBase getSharedLocalInspectionTool() {
    return myLocalInspectionBase;
  }

  private boolean isAddMainsEnabled() {
    return ADD_MAINS_TO_ENTRIES;
  }

  private boolean isAddAppletEnabled() {
    return ADD_APPLET_TO_ENTRIES;
  }

  private boolean isAddServletEnabled() {
    return ADD_SERVLET_TO_ENTRIES;
  }

  private boolean isAddNonJavaUsedEnabled() {
    return ADD_NONJAVA_TO_ENTRIES;
  }

  public boolean isTestEntryPoints() {
    return TEST_ENTRY_POINTS;
  }

  public void setTestEntryPoints(boolean testEntryPoints) {
    TEST_ENTRY_POINTS = testEntryPoints;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    myLocalInspectionBase.readSettings(node);
    for (EntryPoint extension : getExtensions()) {
      extension.readExternal(node);
      saveEntryPointElement(extension);
    }

    final String testEntriesAttr = node.getAttributeValue("test_entries");
    TEST_ENTRY_POINTS = testEntriesAttr == null || Boolean.parseBoolean(testEntriesAttr);
  }

  protected void saveEntryPointElement(@NotNull EntryPoint entryPoint) {
    Element element = new Element("root");
    entryPoint.writeExternal(element);
    entryPointElements.put(entryPoint.getDisplayName(), element);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    myLocalInspectionBase.writeSettings(node);
    writeUnusedDeclarationSettings(node);

    if (!TEST_ENTRY_POINTS) {
      node.setAttribute("test_entries", Boolean.toString(false));
    }
  }

  protected void writeUnusedDeclarationSettings(Element node) throws WriteExternalException {
    super.writeSettings(node);
    for (EntryPoint extension : getExtensions()) {
      extension.writeExternal(node);
    }
  }

  private static boolean isExternalizableNoParameterConstructor(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!method.isConstructor()) return false;
    if (method.getVisibility() != UastVisibility.PUBLIC) return false;
    final List<UParameter> parameterList = method.getUastParameters();
    if (!parameterList.isEmpty()) return false;
    final UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isExternalizable(aClass, refClass);
  }

  private static boolean isSerializationImplicitlyUsedField(@NotNull UField field) {
    final String name = field.getName();
    if (!CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !"serialPersistentFields".equals(name)) return false;
    if (!field.isStatic()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(field, UClass.class);
    return aClass == null || isSerializable(aClass, null);
  }

  private static boolean isWriteObjectMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    final String name = method.getName();
    if (!"writeObject".equals(name)) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (parameters.size() != 1) return false;
    if (!equalsToText(parameters.get(0).getType(), "java.io.ObjectOutputStream")) return false;
    if (method.isStatic()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadObjectMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    final String name = method.getName();
    if (!"readObject".equals(name)) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (parameters.size() != 1) return false;
    if (!equalsToText(parameters.get(0).getType(), "java.io.ObjectInputStream")) return false;
    if (method.isStatic()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadObjectNoDataMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    String name = method.getName();
    if (!"readObjectNoData".equals(name)) return false;
    if (!method.getUastParameters().isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), PsiKeyword.VOID)) return false;
    if (method.isStatic()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isWriteReplaceMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    final String name = method.getName();
    if (!"writeReplace".equals(name)) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (!parameters.isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.isStatic()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadResolveMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    final String name = method.getName();
    if (!"readResolve".equals(name)) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (!parameters.isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.isStatic()) return false;
    final UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean equalsToText(PsiType type, String text) {
    return type != null && type.equalsToText(text);
  }

  private static boolean isSerializable(@NotNull UClass aClass, @Nullable RefClass refClass) {
    return isSerializable(aClass, refClass, "java.io.Serializable");
  }

  private static boolean isExternalizable(@NotNull UClass aClass, @Nullable RefClass refClass) {
    return isSerializable(aClass, refClass, "java.io.Externalizable");
  }

  private static boolean isSerializable(@NotNull UClass aClass, @Nullable RefClass refClass, @NotNull String fqn) {
    PsiClass psiClass = aClass.getJavaPsi();
    Project project = psiClass.getProject();
    final PsiClass serializableClass = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> JavaPsiFacade.getInstance(project).findClass(fqn, psiClass.getResolveScope()));
    return serializableClass != null && isSerializable(aClass, refClass, serializableClass);
  }

  private static boolean isSerializable(@Nullable UClass aClass, @Nullable RefClass refClass, @NotNull PsiClass serializableClass) {
    if (aClass == null) return false;
    if (aClass.getJavaPsi().isInheritor(serializableClass, true)) return true;
    if (refClass != null) {
      final Set<RefClass> subClasses = refClass.getSubClasses();
      for (RefClass subClass : subClasses) {
        //TODO reimplement
        if (isSerializable(subClass.getUastElement(), subClass, serializableClass)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull final AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull final GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull final RefEntity refEntity) {
        if (refEntity instanceof RefElementImpl refElement) {
          if (!refElement.isSuspicious()) return;

          PsiFile file = refElement.getContainingFile();

          if (file == null) return;
          final boolean isSuppressed = refElement.isSuppressed(getShortName(), ALTERNATIVE_ID);
          if (isSuppressed || !((GlobalInspectionContextBase)globalContext).isToCheckFile(file, UnusedDeclarationInspectionBase.this)) {
            if (isSuppressed || !scope.contains(file)) {
              getEntryPointsManager(globalContext).addEntryPoint(refElement, false);
            }
          }
        }
      }
    });

    globalContext.putUserData(PHASE_KEY, 1);
    globalContext.putUserData(PROCESSED_SUSPICIOUS_ELEMENTS_KEY, new HashSet<>());
  }

  public boolean isEntryPoint(@NotNull RefElement owner) {
    PsiElement element = owner.getPsiElement();
    if (owner instanceof RefJavaElement) {
      UElement uElement = ((RefJavaElement)owner).getUastElement();
      if (uElement != null) {
        element = uElement.getJavaPsi();
      }
    }
    if (RefUtil.isImplicitUsage(element)) return true;
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(element.getProject());
      if (entryPointsManager.isEntryPoint(element)) {
        return true;
      }
    }
    if (element != null) {
      for (EntryPoint extension : getExtensions()) {
        if (extension.isSelected() && extension.isEntryPoint(owner, element)) {
          return true;
        }
      }

      if (isAddMainsEnabled() && owner instanceof RefMethod && ((RefMethod)owner).isAppMain()) {
        return true;
      }

      if (owner instanceof RefClass) {
        if (isAddAppletEnabled() && ((RefClass)owner).isApplet() || isAddServletEnabled() && ((RefClass)owner).isServlet()) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isEntryPoint(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (element instanceof PsiMethod && isAddMainsEnabled() && PsiClassImplUtil.isMainOrPremainMethod((PsiMethod)element)) {
      return true;
    }
    if (element instanceof PsiClass aClass) {
      final PsiClass applet = psiFacade.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
      if (isAddAppletEnabled() && applet != null && aClass.isInheritor(applet, true)) {
        return true;
      }

      final PsiClass servlet = psiFacade.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
      if (isAddServletEnabled() && servlet != null && aClass.isInheritor(servlet, true)) {
        return true;
      }
      if (isAddMainsEnabled()) {
        if (hasMainMethodDeep(aClass)) return true;
      }
    }
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(project);
      if (entryPointsManager.isEntryPoint(element)) return true;
    }
    for (EntryPoint extension : getExtensions()) {
      if (extension.isSelected() && extension.isEntryPoint(element)) {
        return true;
      }
    }
    return RefUtil.isImplicitUsage(element);
  }

  private static boolean hasMainMethodDeep(PsiClass aClass) {
    if (PsiMethodUtil.hasMainMethod(aClass)) return true;
    for (PsiClass innerClass : aClass.getInnerClasses()) {
      if (innerClass.hasModifierProperty(PsiModifier.STATIC) && PsiMethodUtil.hasMainMethod(innerClass)) {
        return true;
      }
    }
    return false;
  }

  public boolean isGlobalEnabledInEditor() {
    return myEnabledInEditor;
  }

  @NotNull
  public static UnusedDeclarationInspectionBase findUnusedDeclarationInspection(@NotNull PsiElement element) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(SHORT_NAME, element);
    return tool == null ? new UnusedDeclarationInspectionBase() : tool;
  }

  public static boolean isDeclaredAsEntryPoint(@NotNull PsiElement method) {
    return findUnusedDeclarationInspection(method).isEntryPoint(method);
  }

  private static final class StrictUnreferencedFilter extends UnreferencedFilter {
    private StrictUnreferencedFilter(@NotNull UnusedDeclarationInspectionBase tool, @NotNull GlobalInspectionContext context) {
      super(tool, context);
    }

    @Override
    public int getElementProblemCount(@NotNull RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > -1) return problemCount;
      return refElement.isReferenced() ? 0 : 1;
    }
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    checkForReachableRefs(globalContext);
    int phase = Objects.requireNonNull(globalContext.getUserData(PHASE_KEY));
    Set<RefElement> processedSuspicious = globalContext.getUserData(PROCESSED_SUSPICIOUS_ELEMENTS_KEY);

    final boolean firstPhase = phase == 1;
    final RefFilter filter = firstPhase ? new StrictUnreferencedFilter(this, globalContext) :
                             new RefUnreachableFilter(this, globalContext);
    LOG.assertTrue(processedSuspicious != null, "phase: " + phase);

    final boolean[] requestAdded = {false};
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement refElement)) return;
        if (refEntity instanceof RefClass refClass && refClass.isAnonymous()) return;
        if (filter.accepts(refElement) && !processedSuspicious.contains(refElement)) {
          refEntity.accept(new RefJavaVisitor() {
            @Override
            public void visitField(@NotNull final RefField refField) {
              processedSuspicious.add(refField);
              UField uField = refField.getUastElement();
              if (uField != null && isSerializationImplicitlyUsedField(uField)) {
                getEntryPointsManager(globalContext).addEntryPoint(refField, false);
              }
              else {
                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueFieldUsagesProcessor(refField, psiReference -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refField, false);
                  return false;
                });
                requestAdded[0] = true;
              }
            }

            @Override
            public void visitMethod(@NotNull final RefMethod refMethod) {
              processedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor ctor) {
                RefClass ownerClass = ctor.getOwnerClass();
                visitClass(ownerClass);
                return;
              }
              if (refMethod.isConstructor()) {
                RefClass ownerClass = refMethod.getOwnerClass();
                if (ownerClass != null) {
                  queryQualifiedNameUsages(ownerClass);
                }
              }
              UMethod uMethod = refMethod.getUastElement();
              if (uMethod != null && (isSerializablePatternMethod(uMethod, refMethod.getOwnerClass()) ||
                                      // todo this method potentially leads to INRE. Perhaps, it should be reconsidered/deleted (IJ-CR-5556)
                                      belongsToRepeatableAnnotationContainer(uMethod, refMethod.getOwnerClass()))) {
                getEntryPointsManager(globalContext).addEntryPoint(refMethod, false);
              }
              else if (!refMethod.isExternalOverride() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
                processedSuspicious.addAll(refMethod.getDerivedMethods());
                enqueueMethodUsages(globalContext, refMethod);
                requestAdded[0] = true;
              }
            }

            @Override
            public void visitClass(@NotNull final RefClass refClass) {
              processedSuspicious.add(refClass);
              if (!refClass.isAnonymous()) {
                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueDerivedClassesProcessor(refClass, inheritor -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refClass, false);
                  return false;
                });

                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueClassUsagesProcessor(refClass, psiReference -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refClass, false);
                  return false;
                });

                queryQualifiedNameUsages(refClass);
                requestAdded[0] = true;
              }
            }

            void queryQualifiedNameUsages(@NotNull RefClass refClass) {
              if (firstPhase && isAddNonJavaUsedEnabled()) {
                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueQualifiedNameOccurrencesProcessor(refClass, () -> {
                  EntryPointsManager entryPointsManager = getEntryPointsManager(globalContext);
                  entryPointsManager.addEntryPoint(refClass, false);
                  for (RefMethod constructor : refClass.getConstructors()) {
                    entryPointsManager.addEntryPoint(constructor, false);
                  }
                });

                //references from java-like are already in graph or
                //they would be checked during GlobalJavaInspectionContextImpl.performPostRunActivities
                for (RefElement element : refClass.getInReferences()) {
                  if (!(element instanceof RefJavaElement)) {
                    getEntryPointsManager(globalContext).addEntryPoint(refElement, false);
                  }
                }
                requestAdded[0] = true;
              }
            }
          });
        }
      }
    });

    if (!requestAdded[0]) {
      if (phase == 2) {
        globalContext.putUserData(PROCESSED_SUSPICIOUS_ELEMENTS_KEY, null);
        return false;
      }
      else {
        globalContext.putUserData(PHASE_KEY, 2);
      }
    }

    return true;
  }

  private static boolean isSerializablePatternMethod(@NotNull UMethod psiMethod, @Nullable RefClass refClass) {
    return isReadObjectMethod(psiMethod, refClass) ||
           isReadObjectNoDataMethod(psiMethod, refClass) ||
           isWriteObjectMethod(psiMethod, refClass) ||
           isReadResolveMethod(psiMethod, refClass) ||
           isWriteReplaceMethod(psiMethod, refClass) ||
           isExternalizableNoParameterConstructor(psiMethod, refClass);
  }

  private static boolean belongsToRepeatableAnnotationContainer(@NotNull UMethod uMethod, @Nullable RefClass ownerRefClass) {
    if (ownerRefClass == null) return false;
    if (!PsiUtil.isLanguageLevel8OrHigher(uMethod.getJavaPsi())) return false;
    if (!"value".equals(uMethod.getName())) return false;
    if (!ownerRefClass.isAnnotationType()) return false;
    PsiType returnType = uMethod.getReturnType();
    if (!(returnType instanceof PsiArrayType)) return false;
    PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
    if (returnTypeClass == null || !returnTypeClass.isAnnotationType()) return false;
    RefElement repeatableAnn = ownerRefClass.getRefManager().getReference(returnTypeClass);
    if (repeatableAnn == null || !repeatableAnn.isReferenced()) return false;
    PsiAnnotation repeatableAnnContainer = returnTypeClass.getAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE);
    if (repeatableAnnContainer == null) return false;
    return AnnotationsHighlightUtil.doCheckRepeatableAnnotation(repeatableAnnContainer) == null;
  }

  private static void enqueueMethodUsages(GlobalInspectionContext globalContext, final RefMethod refMethod) {
    if (refMethod.getSuperMethods().isEmpty()) {
      globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueMethodUsagesProcessor(refMethod, psiReference -> {
        getEntryPointsManager(globalContext).addEntryPoint(refMethod, false);
        return false;
      });
    }
    else {
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        enqueueMethodUsages(globalContext, refSuper);
      }
    }
  }

  @Override
  public JobDescriptor @Nullable [] getAdditionalJobs(@NotNull GlobalInspectionContext context) {
    return new JobDescriptor[]{context.getStdJobDescriptors().BUILD_GRAPH, context.getStdJobDescriptors().FIND_EXTERNAL_USAGES};
  }

  void checkForReachableRefs(@NotNull final GlobalInspectionContext context) {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    RefManager refManager = context.getRefManager();
    refManager.iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefJavaElementImpl refElement) {
          if (!((GlobalInspectionContextBase)context).isToCheckMember(refElement, UnusedDeclarationInspectionBase.this)) return;
          refElement.setReachable(false);
        }
      }
    });

    for (RefElement entry : getEntryPointsManager(context).getEntryPoints(refManager)) {
      entry.accept(codeScanner);
    }

    while (!codeScanner.myNextRound.isEmpty()) {
      codeScanner.myNextRound.pop().accept(codeScanner);
    }

    while (codeScanner.newlyInstantiatedClassesCount() != 0) {
      codeScanner.cleanInstantiatedClassesCount();
      codeScanner.processDelayedMethods();

      while (!codeScanner.myNextRound.isEmpty()) {
        codeScanner.myNextRound.pop().accept(codeScanner);
      }
    }
  }

  private static EntryPointsManager getEntryPointsManager(final GlobalInspectionContext context) {
    return context.getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(context.getRefManager());
  }

  private static class CodeScanner extends RefJavaVisitor {
    private final Map<RefClass, Set<RefMethod>> myClassIDtoMethods = new HashMap<>();
    private final Set<RefClass> myInstantiatedClasses = new HashSet<>();
    private int myInstantiatedClassesCount;
    private final Set<RefClass> myProcessedClasses = new HashSet<>();
    private final Set<RefMethod> myProcessedMethods = new HashSet<>();
    private final Set<RefFunctionalExpression> myProcessedFunctionalExpressions = new HashSet<>();
    private final Stack<RefElement> myNextRound = new Stack<>();

    @Override
    public void visitMethod(@NotNull RefMethod method) {
      if (!myProcessedMethods.contains(method)) {
        // Process class's static initializers
        RefClass methodOwnerClass = method.getOwnerClass();
        if (method.isStatic() || method.isConstructor() || method.isEntry()) {
          if (method.isStatic()) {
            RefElementImpl owner = (RefElementImpl)method.getOwner();
            if (owner != null) {
              owner.setReachable(true);
            }
          }
          else if (methodOwnerClass != null) {
            addInstantiatedClass(methodOwnerClass);
          }
          myProcessedMethods.add(method);
          makeContentReachable((RefJavaElementImpl)method);
          makeReachable(methodOwnerClass);
        }
        else {
          if (methodOwnerClass == null || isClassInstantiated(methodOwnerClass)) {
            myProcessedMethods.add(method);
            makeContentReachable((RefJavaElementImpl)method);
          }
          else {
            addDelayedMethod(method, methodOwnerClass);
          }

          for (RefOverridable reference : method.getDerivedReferences()) {
            if (reference instanceof RefMethod) {
              visitMethod(((RefMethod)reference));
            }
            else if (reference instanceof RefFunctionalExpression) {
              visitFunctionalExpression(((RefFunctionalExpression)reference));
            }
          }
        }
      }
    }

    @Override
    public void visitFunctionalExpression(@NotNull RefFunctionalExpression functionalExpression) {
      if (myProcessedFunctionalExpressions.add(functionalExpression)) {
        makeContentReachable((RefJavaElementImpl)functionalExpression);
      }
    }

    @Override public void visitClass(@NotNull RefClass refClass) {
      if (myProcessedClasses.add(refClass)) {
        ((RefClassImpl)refClass).setReachable(true);
        // Process class's static initializers.
        makeReachable(refClass);

        addInstantiatedClass(refClass);
      }
    }

    @Override public void visitField(@NotNull RefField field) {
      // Process class's static initializers.
      if (!field.isReachable()) {
        makeContentReachable((RefJavaElementImpl)field);
        makeReachable(field.getOwnerClass());
      }
    }

    private void addInstantiatedClass(@NotNull RefClass refClass) {
      if (myInstantiatedClasses.add(refClass)) {
        ((RefClassImpl)refClass).setReachable(true);
        myInstantiatedClassesCount++;

        final List<RefMethod> refMethods = refClass.getLibraryMethods();
        for (RefMethod refMethod : refMethods) {
          refMethod.accept(this);
        }
        for (RefClass baseClass : refClass.getBaseClasses()) {
          addInstantiatedClass(baseClass);
        }
      }
    }

    private void makeContentReachable(RefJavaElementImpl refElement) {
      refElement.setReachable(true);
      makeReachable(refElement);
    }

    private void makeReachable(@Nullable RefElement refElement) {
      if (refElement == null) return;
      myNextRound.addAll(refElement.getOutReferences());
    }

    private void addDelayedMethod(@NotNull RefMethod refMethod, @NotNull RefClass ownerClass) {
      Set<RefMethod> methods = myClassIDtoMethods.computeIfAbsent(ownerClass, __ -> new HashSet<>());
      methods.add(refMethod);
    }

    private boolean isClassInstantiated(@NotNull RefClass refClass) {
      return refClass.isUtilityClass() || myInstantiatedClasses.contains(refClass);
    }

    private int newlyInstantiatedClassesCount() {
      return myInstantiatedClassesCount;
    }

    private void cleanInstantiatedClassesCount() {
      myInstantiatedClassesCount = 0;
    }

    private void processDelayedMethods() {
      RefClass[] instClasses = myInstantiatedClasses.toArray(new RefClass[0]);
      for (RefClass refClass : instClasses) {
        if (isClassInstantiated(refClass)) {
          Set<RefMethod> methods = myClassIDtoMethods.get(refClass);
          if (methods != null) {
            RefMethod[] arMethods = methods.toArray(new RefMethod[0]);
            for (RefMethod arMethod : arMethods) {
              arMethod.accept(this);
            }
          }
        }
      }
    }
  }

  @NotNull
  public List<EntryPoint> getExtensions() {
    List<EntryPoint> extensions = EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
    List<EntryPoint> deadCodeAddIns = new ArrayList<>(extensions.size());
    for (EntryPoint entryPoint : extensions) {
      try {
        EntryPoint clone = entryPoint.clone();
        Element element = entryPointElements.get(entryPoint.getDisplayName());
        if (element != null) {
          clone.readExternal(element);
        }
        deadCodeAddIns.add(clone);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    deadCodeAddIns.sort((o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName()));
    return deadCodeAddIns;
  }

  @Contract(pure = true)
  public static @NotNull String getDisplayNameText() {
    return AnalysisBundle.message("inspection.dead.code.display.name");
  }
}