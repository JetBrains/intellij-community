/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.ToolExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author max
 * @since Oct 12, 2001
 */
public class UnusedDeclarationInspectionBase extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance(UnusedDeclarationInspectionBase.class);

  public boolean ADD_MAINS_TO_ENTRIES = true;
  public boolean ADD_APPLET_TO_ENTRIES = true;
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  public boolean ADD_NONJAVA_TO_ENTRIES = true;
  private boolean TEST_ENTRY_POINTS = true;

  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.dead.code.display.name");
  public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  public static final String ALTERNATIVE_ID = "UnusedDeclaration";

  final List<EntryPoint> myExtensions = ContainerUtil.createLockFreeCopyOnWriteList();
  final UnusedSymbolLocalInspectionBase myLocalInspectionBase = createUnusedSymbolLocalInspection();

  private Set<RefElement> myProcessedSuspicious;
  private int myPhase;
  private final boolean myEnabledInEditor;

  @SuppressWarnings("TestOnlyProblems")
  public UnusedDeclarationInspectionBase() {
    this(!ApplicationManager.getApplication().isUnitTestMode());
  }

  @TestOnly
  public UnusedDeclarationInspectionBase(boolean enabledInEditor) {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.DEAD_CODE_TOOL);
    EntryPoint[] extensions = point.getExtensions();
    List<EntryPoint> deadCodeAddIns = new ArrayList<>(extensions.length);
    for (EntryPoint entryPoint : extensions) {
      try {
        deadCodeAddIns.add(entryPoint.clone());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    Collections.sort(deadCodeAddIns, (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName()));
    myExtensions.addAll(deadCodeAddIns);
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
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
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
    for (EntryPoint extension : myExtensions) {
      extension.readExternal(node);
    }

    final String testEntriesAttr = node.getAttributeValue("test_entries");
    TEST_ENTRY_POINTS = testEntriesAttr == null || Boolean.parseBoolean(testEntriesAttr);
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
    for (EntryPoint extension : myExtensions) {
      extension.writeExternal(node);
    }
  }

  private static boolean isExternalizableNoParameterConstructor(@NotNull PsiMethod method, RefClass refClass) {
    if (!method.isConstructor()) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) return false;
    final PsiClass aClass = method.getContainingClass();
    return aClass == null || isExternalizable(aClass, refClass);
  }

  private static boolean isSerializationImplicitlyUsedField(@NotNull PsiField field) {
    final String name = field.getName();
    if (!HighlightUtilBase.SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !"serialPersistentFields".equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass, null);
  }

  private static boolean isWriteObjectMethod(@NotNull PsiMethod method, RefClass refClass) {
    final String name = method.getName();
    if (!"writeObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!equalsToText(parameters[0].getType(), "java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadObjectMethod(@NotNull PsiMethod method, RefClass refClass) {
    final String name = method.getName();
    if (!"readObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!equalsToText(parameters[0].getType(), "java.io.ObjectInputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isWriteReplaceMethod(@NotNull PsiMethod method, RefClass refClass) {
    final String name = method.getName();
    if (!"writeReplace".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadResolveMethod(@NotNull PsiMethod method, RefClass refClass) {
    final String name = method.getName();
    if (!"readResolve".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    final PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean equalsToText(PsiType type, String text) {
    return type != null && type.equalsToText(text);
  }

  private static boolean isSerializable(PsiClass aClass, @Nullable RefClass refClass) {
    final PsiClass serializableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io.Serializable", aClass.getResolveScope());
    return serializableClass != null && isSerializable(aClass, refClass, serializableClass);
  }

  private static boolean isExternalizable(@NotNull PsiClass aClass, RefClass refClass) {
    final GlobalSearchScope scope = aClass.getResolveScope();
    final PsiClass externalizableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io.Externalizable", scope);
    return externalizableClass != null && isSerializable(aClass, refClass, externalizableClass);
  }

  private static boolean isSerializable(PsiClass aClass, RefClass refClass, PsiClass serializableClass) {
    if (aClass == null) return false;
    if (aClass.isInheritor(serializableClass, true)) return true;
    if (refClass != null) {
      final Set<RefClass> subClasses = refClass.getSubClasses();
      for (RefClass subClass : subClasses) {
        if (isSerializable(subClass.getElement(), subClass, serializableClass)) return true;
      }
    }
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
        if (refEntity instanceof RefElementImpl) {
          final RefElementImpl refElement = (RefElementImpl)refEntity;
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

    if (isAddNonJavaUsedEnabled()) {
      checkForReachableRefs(globalContext);
      final StrictUnreferencedFilter strictUnreferencedFilter = new StrictUnreferencedFilter(this, globalContext);
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          final RefManager refManager = globalContext.getRefManager();
          final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(refManager.getProject());
          refManager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@NotNull final RefEntity refEntity) {
              if (refEntity instanceof RefClass && strictUnreferencedFilter.accepts((RefClass)refEntity)) {
                findExternalClassReferences((RefClass)refEntity);
              }
              else if (refEntity instanceof RefMethod) {
                RefMethod refMethod = (RefMethod)refEntity;
                if (refMethod.isConstructor() && strictUnreferencedFilter.accepts(refMethod)) {
                  findExternalClassReferences(refMethod.getOwnerClass());
                }
              }
            }

            private void findExternalClassReferences(final RefClass refElement) {
              final PsiClass psiClass = refElement.getElement();
              String qualifiedName = psiClass != null ? psiClass.getQualifiedName() : null;
              if (qualifiedName != null) {
                final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(globalContext.getProject());
                final PsiNonJavaFileReferenceProcessor processor = (file, startOffset, endOffset) -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refElement, false);
                  return false;
                };
                final DelegatingGlobalSearchScope globalSearchScope = new DelegatingGlobalSearchScope(projectScope) {
                  @Override
                  public boolean contains(@NotNull VirtualFile file) {
                    return file.getFileType() != JavaFileType.INSTANCE && super.contains(file);
                  }
                };

                helper.processUsagesInNonJavaFiles(qualifiedName, processor, globalSearchScope);

                //references from java-like are already in graph or
                //they would be checked during GlobalJavaInspectionContextImpl.performPostRunActivities
                for (RefElement element : refElement.getInReferences()) {
                  if (!(element instanceof RefJavaElement)) {
                    getEntryPointsManager(globalContext).addEntryPoint(refElement, false);
                  }
                }
              }
            }
          });
        }
      }, null);
    }

    myProcessedSuspicious = new HashSet<>();
    myPhase = 1;
  }

  public boolean isEntryPoint(@NotNull RefElement owner) {
    final PsiElement element = owner.getElement();
    if (RefUtil.isImplicitUsage(element)) return true;
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(element.getProject());
      if (entryPointsManager.isEntryPoint(element)) {
        return true;
      }
    }
    if (element != null) {
      for (EntryPoint extension : myExtensions) {
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
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      final PsiClass applet = psiFacade.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
      if (isAddAppletEnabled() && applet != null && aClass.isInheritor(applet, true)) {
        return true;
      }

      final PsiClass servlet = psiFacade.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
      if (isAddServletEnabled() && servlet != null && aClass.isInheritor(servlet, true)) {
        return true;
      }
      if (isAddMainsEnabled() && PsiMethodUtil.hasMainMethod(aClass)) return true;
    }
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(project);
      if (entryPointsManager.isEntryPoint(element)) return true;
    }
    for (EntryPoint extension : myExtensions) {
      if (extension.isSelected() && extension.isEntryPoint(element)) {
        return true;
      }
    }
    return RefUtil.isImplicitUsage(element);
  }

  public boolean isGlobalEnabledInEditor() {
    return myEnabledInEditor;
  }

  private static class StrictUnreferencedFilter extends UnreferencedFilter {
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
    final RefFilter filter = myPhase == 1 ? new StrictUnreferencedFilter(this, globalContext) :
                             new RefUnreachableFilter(this, globalContext);
    LOG.assertTrue(myProcessedSuspicious != null, "phase: " + myPhase);

    final boolean[] requestAdded = {false};
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;
        if (refEntity instanceof RefClass && ((RefClass)refEntity).isAnonymous()) return;
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (filter.accepts(refElement) && !myProcessedSuspicious.contains(refElement)) {
          refEntity.accept(new RefJavaVisitor() {
            @Override
            public void visitField(@NotNull final RefField refField) {
              myProcessedSuspicious.add(refField);
              PsiField psiField = refField.getElement();
              if (psiField != null && isSerializationImplicitlyUsedField(psiField)) {
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
              myProcessedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor) {
                visitClass(refMethod.getOwnerClass());
              }
              else {
                PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
                if (psiMethod != null && isSerializablePatternMethod(psiMethod, refMethod.getOwnerClass())) {
                  getEntryPointsManager(globalContext).addEntryPoint(refMethod, false);
                }
                else if (!refMethod.isExternalOverride() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
                  for (final RefMethod derivedMethod : refMethod.getDerivedMethods()) {
                    myProcessedSuspicious.add(derivedMethod);
                  }

                  enqueueMethodUsages(globalContext, refMethod);
                  requestAdded[0] = true;
                }
              }
            }

            @Override
            public void visitClass(@NotNull final RefClass refClass) {
              myProcessedSuspicious.add(refClass);
              if (!refClass.isAnonymous()) {
                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueDerivedClassesProcessor(refClass, inheritor -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refClass, false);
                  return false;
                });

                globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT).enqueueClassUsagesProcessor(refClass, psiReference -> {
                  getEntryPointsManager(globalContext).addEntryPoint(refClass, false);
                  return false;
                });
                requestAdded[0] = true;
              }
            }
          });
        }
      }
    });

    if (!requestAdded[0]) {
      if (myPhase == 2) {
        myProcessedSuspicious = null;
        return false;
      }
      else {
        myPhase = 2;
      }
    }

    return true;
  }

  private static boolean isSerializablePatternMethod(@NotNull PsiMethod psiMethod, RefClass refClass) {
    return isReadObjectMethod(psiMethod, refClass) || isWriteObjectMethod(psiMethod, refClass) || isReadResolveMethod(psiMethod, refClass) ||
           isWriteReplaceMethod(psiMethod, refClass) || isExternalizableNoParameterConstructor(psiMethod, refClass);
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

  @Nullable
  @Override
  public JobDescriptor[] getAdditionalJobs(GlobalInspectionContext context) {
    return new JobDescriptor[]{context.getStdJobDescriptors().BUILD_GRAPH, context.getStdJobDescriptors().FIND_EXTERNAL_USAGES};
  }


  void checkForReachableRefs(@NotNull final GlobalInspectionContext context) {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    context.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefJavaElementImpl) {
          final RefJavaElementImpl refElement = (RefJavaElementImpl)refEntity;
          if (!((GlobalInspectionContextBase)context).isToCheckMember(refElement, UnusedDeclarationInspectionBase.this)) return;
          refElement.setReachable(false);
        }
      }
    });


    for (RefElement entry : getEntryPointsManager(context).getEntryPoints()) {
      entry.accept(codeScanner);
    }

    while (codeScanner.newlyInstantiatedClassesCount() != 0) {
      codeScanner.cleanInstantiatedClassesCount();
      codeScanner.processDelayedMethods();
    }
  }

  private static EntryPointsManager getEntryPointsManager(final GlobalInspectionContext context) {
    return context.getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(context.getRefManager());
  }

  private static class CodeScanner extends RefJavaVisitor {
    private final Map<RefClass, Set<RefMethod>> myClassIDtoMethods = new HashMap<>();
    private final Set<RefClass> myInstantiatedClasses = new HashSet<>();
    private int myInstantiatedClassesCount;
    private final Set<RefMethod> myProcessedMethods = new HashSet<>();

    @Override public void visitMethod(@NotNull RefMethod method) {
      if (!myProcessedMethods.contains(method)) {
        // Process class's static initializers
        if (method.isStatic() || method.isConstructor()) {
          if (method.isConstructor()) {
            addInstantiatedClass(method.getOwnerClass());
          }
          else {
            ((RefClassImpl)method.getOwnerClass()).setReachable(true);
          }
          myProcessedMethods.add(method);
          makeContentReachable((RefJavaElementImpl)method);
          makeClassInitializersReachable(method.getOwnerClass());
        }
        else {
          if (isClassInstantiated(method.getOwnerClass())) {
            myProcessedMethods.add(method);
            makeContentReachable((RefJavaElementImpl)method);
          }
          else {
            addDelayedMethod(method);
          }

          for (RefMethod refSub : method.getDerivedMethods()) {
            visitMethod(refSub);
          }
        }
      }
    }

    @Override public void visitClass(@NotNull RefClass refClass) {
      boolean alreadyActive = refClass.isReachable();
      ((RefClassImpl)refClass).setReachable(true);

      if (!alreadyActive) {
        // Process class's static initializers.
        makeClassInitializersReachable(refClass);
      }

      addInstantiatedClass(refClass);
    }

    @Override public void visitField(@NotNull RefField field) {
      // Process class's static initializers.
      if (!field.isReachable()) {
        makeContentReachable((RefJavaElementImpl)field);
        makeClassInitializersReachable(field.getOwnerClass());
      }
    }

    private void addInstantiatedClass(RefClass refClass) {
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
      for (RefElement refCallee : refElement.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void makeClassInitializersReachable(RefClass refClass) {
      for (RefElement refCallee : refClass.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void addDelayedMethod(RefMethod refMethod) {
      Set<RefMethod> methods = myClassIDtoMethods.get(refMethod.getOwnerClass());
      if (methods == null) {
        methods = new HashSet<>();
        myClassIDtoMethods.put(refMethod.getOwnerClass(), methods);
      }
      methods.add(refMethod);
    }

    private boolean isClassInstantiated(RefClass refClass) {
      return myInstantiatedClasses.contains(refClass);
    }

    private int newlyInstantiatedClassesCount() {
      return myInstantiatedClassesCount;
    }

    private void cleanInstantiatedClassesCount() {
      myInstantiatedClassesCount = 0;
    }

    private void processDelayedMethods() {
      RefClass[] instClasses = myInstantiatedClasses.toArray(new RefClass[myInstantiatedClasses.size()]);
      for (RefClass refClass : instClasses) {
        if (isClassInstantiated(refClass)) {
          Set<RefMethod> methods = myClassIDtoMethods.get(refClass);
          if (methods != null) {
            RefMethod[] arMethods = methods.toArray(new RefMethod[methods.size()]);
            for (RefMethod arMethod : arMethods) {
              arMethod.accept(this);
            }
          }
        }
      }
    }
  }

  public List<EntryPoint> getExtensions() {
    return myExtensions;
  }
}