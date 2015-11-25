/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaMethodElementType;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.containers.ContainerUtilRt.newHashSet;

public class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#" + JavaFunctionalExpressionSearcher.class.getName());
  /**
   * The least number of candidate files with functional expressions that directly scanning them becomes expensive
   * and more advanced ways of searching become necessary: e.g. first searching for methods where the functional interface class is used
   * and then for their usages,
    */
  @VisibleForTesting
  public static final int SMART_SEARCH_THRESHOLD = 5;

  @Override
  public void processQuery(@NotNull FunctionalExpressionSearch.SearchParameters queryParameters,
                           @NotNull Processor<PsiFunctionalExpression> consumer) {
    final GlobalSearchScope useScope;
    final PsiClass aClass;
    final Project project;
    final int expectedFunExprParamsCount;

    AccessToken token = ReadAction.start();
    try {
      aClass = queryParameters.getElementToSearch();
      if (!aClass.isValid() || !LambdaUtil.isFunctionalClass(aClass)) return;

      project = aClass.getProject();
      final Set<Module> highLevelModules = getJava8Modules(project);
      if (highLevelModules.isEmpty()) return;

      useScope = convertToGlobalScope(project, queryParameters.getEffectiveSearchScope());

      final MethodSignature functionalInterfaceMethod = LambdaUtil.getFunction(aClass);
      LOG.assertTrue(functionalInterfaceMethod != null);
      expectedFunExprParamsCount = functionalInterfaceMethod.getParameterTypes().length;
    } finally {
      token.finish();
    }

    //collect all files with '::' and '->' in useScope
    Set<VirtualFile> candidateFiles = getFilesWithFunctionalExpressionsScope(project, new JavaSourceFilterScope(useScope));
    if (candidateFiles.size() < SMART_SEARCH_THRESHOLD) {
      searchInFiles(aClass, consumer, candidateFiles, expectedFunExprParamsCount);
      return;
    }

    final GlobalSearchScope candidateScope = GlobalSearchScope.filesScope(project, candidateFiles);

    //collect all methods with parameter of functional interface or free type parameter type
    final Collection<PsiMethod> methodCandidates = getCandidateMethodsWithSuitableParams(aClass, project, useScope, candidateFiles, candidateScope);

    final LinkedHashSet<VirtualFile> filesToProcess = new LinkedHashSet<VirtualFile>();
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();

    //find all usages of method candidates in files with functional expressions
    for (final PsiMethod psiMethod : methodCandidates) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (!psiMethod.isValid()) return;
          final int parametersCount = psiMethod.getParameterList().getParametersCount();
          final boolean varArgs = psiMethod.isVarArgs();
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          final GlobalSearchScope methodUseScope = convertToGlobalScope(project, psiMethod.getUseScope());
          final LinkedHashMap<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> holders = new LinkedHashMap<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>>();
          //functional expressions checker: number and type of parameters at call site should correspond to candidate method currently check
          final SuitableFilesProcessor processor = new SuitableFilesProcessor(holders, expectedFunExprParamsCount, parametersCount, varArgs, parameters);
          fileBasedIndex.processValues(JavaFunctionalExpressionIndex.JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID, psiMethod.getName(), null, processor, useScope.intersectWith(methodUseScope));
          for (Map.Entry<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> entry : holders.entrySet()) {
            for (JavaFunctionalExpressionIndex.IndexHolder holder : entry.getValue()) {
              if (processor.canBeFunctional(holder)) {
                filesToProcess.add(entry.getKey());
                break;
              }
            }
          }
        }
      });
    }

    //search for functional expressions in non-call contexts
    collectFilesWithTypeOccurrencesAndFieldAssignments(aClass, candidateScope, filesToProcess);

    searchInFiles(aClass, consumer, filesToProcess, expectedFunExprParamsCount);
  }

  @NotNull
  private static Set<Module> getJava8Modules(Project project) {
    final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

    final Set<Module> highLevelModules = new HashSet<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
      if (extension != null) {
        final LanguageLevel level = extension.getLanguageLevel();
        if (level == null && projectLevelIsHigh || level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) {
          highLevelModules.add(module);
        }
      }
    }
    return highLevelModules;
  }

  private static void searchInFiles(final PsiClass aClass,
                                       final Processor<PsiFunctionalExpression> consumer,
                                       Set<VirtualFile> filesToProcess, final int expectedFunExprParamsCount) {
    LOG.info("#usage files: " + filesToProcess.size());
    process(filesToProcess, new ReadActionProcessor<VirtualFile>() {
      @Override
      public boolean processInReadAction(VirtualFile file) {
        //resolve functional expressions to ensure that functional expression type is appropriate
        return processFileWithFunctionalInterfaces(aClass, expectedFunExprParamsCount, consumer, file);
      }
    });
  }

  private static Collection<PsiMethod> getCandidateMethodsWithSuitableParams(final PsiClass aClass,
                                                                             final Project project,
                                                                             final GlobalSearchScope useScope,
                                                                             final Set<VirtualFile> candidateFiles,
                                                                             final GlobalSearchScope candidateScope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiMethod>>() {
        @Override
        public Collection<PsiMethod> compute() {
          if (!aClass.isValid()) return Collections.emptyList();

          GlobalSearchScope visibleFromCandidates = combineResolveScopes(project, candidateFiles);

          final Set<String> usedMethodNames = newHashSet();
          FileBasedIndex.getInstance().processAllKeys(JavaFunctionalExpressionIndex.JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID,
                                                      new CommonProcessors.CollectProcessor<String>(usedMethodNames), candidateScope, null);

          final LinkedHashSet<PsiMethod> methods = newLinkedHashSet();
          Processor<PsiMethod> methodProcessor = new Processor<PsiMethod>() {
            @Override
            public boolean process(PsiMethod method) {
              if (usedMethodNames.contains(method.getName())) {
                methods.add(method);
              }
              return true;
            }
          };

          StubIndexKey<String, PsiMethod> key = JavaMethodParameterTypesIndex.getInstance().getKey();
          StubIndex index = StubIndex.getInstance();
          index.processElements(key, aClass.getName(), project, useScope.intersectWith(visibleFromCandidates), PsiMethod.class, methodProcessor);
          index.processElements(key, JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project, visibleFromCandidates, PsiMethod.class, methodProcessor);
          LOG.info("#methods: " + methods.size());
          return methods;
        }
      });
  }

  @NotNull
  private static GlobalSearchScope combineResolveScopes(Project project, Set<VirtualFile> candidateFiles) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    LinkedHashSet<GlobalSearchScope> resolveScopes = newLinkedHashSet(mapNotNull(candidateFiles, new Function<VirtualFile, GlobalSearchScope>() {
      @Override
      public GlobalSearchScope fun(VirtualFile file) {
        PsiFile psiFile = file.isValid() ? psiManager.findFile(file) : null;
        return psiFile == null ? null : psiFile.getResolveScope();
      }
    }));
    return GlobalSearchScope.union(resolveScopes.toArray(new GlobalSearchScope[resolveScopes.size()]));
  }

  @NotNull
  private static Set<VirtualFile> getFilesWithFunctionalExpressionsScope(Project project, GlobalSearchScope useScope) {
    final Set<VirtualFile> files = newLinkedHashSet();
    final PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
    final CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>(files);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "::", processor);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "->", processor);
    return files;
  }

  @NotNull
  private static GlobalSearchScope convertToGlobalScope(Project project, SearchScope useScope) {
    final GlobalSearchScope scope;
    if (useScope instanceof GlobalSearchScope) {
      scope = (GlobalSearchScope)useScope;
    }
    else if (useScope instanceof LocalSearchScope) {
      final Set<VirtualFile> files = new HashSet<VirtualFile>();
      addAllNotNull(files, map(((LocalSearchScope)useScope).getScope(), new Function<PsiElement, VirtualFile>() {
        @Override
        public VirtualFile fun(PsiElement element) {
          return PsiUtilCore.getVirtualFile(element);
        }
      }));
      scope = GlobalSearchScope.filesScope(project, files);
    }
    else {
      scope = new EverythingGlobalScope(project);
    }
    return scope;
  }
  
  /**
   * Collect files where:
   *  aClass is used, e.g. in type declaration or method return type;
   *  fields with type aClass are used on the left side of assignments. Should find Bar of the following example
    <pre/>
    class Foo {
        Runnable myRunnable;
    }
    
    class Bar {
      void foo(Foo foo){
        foo.myRunnable = () -> {};
      }
    }
    </pre>
   */
  private static void collectFilesWithTypeOccurrencesAndFieldAssignments(PsiClass aClass,
                                                                         GlobalSearchScope filesScope,
                                                                         final LinkedHashSet<VirtualFile> usageFiles) {
    final Set<PsiField> fields = new LinkedHashSet<PsiField>();
    for (final PsiReference reference : ReferencesSearch.search(aClass, filesScope)) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiElement element = reference.getElement();
          if (element != null) {
            addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(element));
            final PsiElement parent = element.getParent();
            if (parent instanceof PsiTypeElement) {
              final PsiElement gParent = parent.getParent();
              if (gParent instanceof PsiField &&
                  !((PsiField)gParent).hasModifierProperty(PsiModifier.PRIVATE) &&
                  !((PsiField)gParent).hasModifierProperty(PsiModifier.FINAL)) {
                fields.add((PsiField)gParent);
              }
            }
          }
        }
      });
    }

    for (PsiField field : fields) {
      ReferencesSearch.search(field, filesScope).forEach(new ReadActionProcessor<PsiReference>() {
        @Override
        public boolean processInReadAction(PsiReference fieldRef) {
          final PsiElement fieldElement = fieldRef.getElement();
          final PsiAssignmentExpression varElementParent = PsiTreeUtil.getParentOfType(fieldElement, PsiAssignmentExpression.class);
          if (varElementParent != null && PsiTreeUtil.isAncestor(varElementParent.getLExpression(), fieldElement, false)) {
            addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(fieldElement));
          }
          return true;
        }
      });
    }
  }

  private static boolean processFileWithFunctionalInterfaces(final PsiClass aClass,
                                                             final int expectedParamCount,
                                                             final Processor<PsiFunctionalExpression> consumer,
                                                             VirtualFile file) {
    final PsiFile psiFile = aClass.getManager().findFile(file);
    if (psiFile != null) {
      final Ref<Boolean> ref = new Ref<Boolean>(true);
      psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (!ref.get()) {
            return;
          }
          super.visitElement(element);
        }

        private void visitFunctionalExpression(PsiFunctionalExpression expression) {
          PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
          if (InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(functionalInterfaceType), aClass, true)) {
            if (!consumer.process(expression)) {
              ref.set(false);
            }
          }
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
          super.visitLambdaExpression(expression);
          if (expression.getParameterList().getParametersCount() == expectedParamCount) {
            visitFunctionalExpression(expression);
          }
        }

        @Override
        public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
          super.visitMethodReferenceExpression(expression);
          visitFunctionalExpression(expression);
        }
      });
      if (!ref.get()) return false;
    }
    return true;
  }

  private static class SuitableFilesProcessor implements FileBasedIndex.ValueProcessor<Collection<JavaFunctionalExpressionIndex.IndexHolder>> {
    private final Map<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> myHolders;
    private final int myExpectedFunExprParamsCount;
    private final int myParametersCount;
    private final boolean myVarArgs;
    private final PsiParameter[] myParameters;

    public SuitableFilesProcessor(Map<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> holders,
                                  int expectedFunExprParamsCount,
                                  int parametersCount,
                                  boolean varArgs,
                                  PsiParameter[] parameters) {
      myHolders = holders;
      myExpectedFunExprParamsCount = expectedFunExprParamsCount;
      myParametersCount = parametersCount;
      myVarArgs = varArgs;
      myParameters = parameters;
    }

    @Override
    public boolean process(VirtualFile file, Collection<JavaFunctionalExpressionIndex.IndexHolder> holders) {
      Set<JavaFunctionalExpressionIndex.IndexHolder> savedHolders = myHolders.get(file);
      for (JavaFunctionalExpressionIndex.IndexHolder holder : holders) {
        final int lambdaParamsNumber = holder.getLambdaParamsNumber();
        if (lambdaParamsNumber == myExpectedFunExprParamsCount || lambdaParamsNumber == -1) {
          final boolean suitableParamNumbers;
          if (myVarArgs) {
            suitableParamNumbers = holder.getMethodArgsLength() >= myParametersCount - 1;
          }
          else {
            suitableParamNumbers = holder.getMethodArgsLength() == myParametersCount;
          }
          if (suitableParamNumbers) {
            if (savedHolders == null) {
              savedHolders = new LinkedHashSet<JavaFunctionalExpressionIndex.IndexHolder>();
              myHolders.put(file, savedHolders);
            }
            savedHolders.add(holder);
            break;
          }
        }
      }
      return true;
    }

    private boolean canBeFunctional(JavaFunctionalExpressionIndex.IndexHolder holder) {
      final int paramIdx = holder.getFunctionExpressionIndex();
      PsiType paramType = myParameters[paramIdx >= myParametersCount ? myParametersCount - 1 : paramIdx].getType();
      if (paramType instanceof PsiEllipsisType) {
        paramType = ((PsiEllipsisType)paramType).getComponentType();
      }
      final PsiClass functionalCandidate = PsiUtil.resolveClassInClassTypeOnly(paramType);
      return functionalCandidate instanceof PsiTypeParameter ||
             LambdaUtil.isFunctionalClass(functionalCandidate);
    }
  }
}
