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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
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
import com.intellij.psi.util.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaFunctionalExpressionSearcher implements QueryExecutor<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {

  private static final Logger LOG = Logger.getInstance("#" + JavaFunctionalExpressionSearcher.class.getName());

  @Override
  public boolean execute(@NotNull final FunctionalExpressionSearch.SearchParameters queryParameters,
                         @NotNull final Processor<PsiFunctionalExpression> consumer) {
    final PsiClass aClass = queryParameters.getElementToSearch();
    final Set<Module> highLevelModules = new HashSet<Module>();
    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (LambdaUtil.isFunctionalClass(aClass)) {
          final Project project = aClass.getProject();
          final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

          for (Module module : ModuleManager.getInstance(project).getModules()) {
            final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
            if (extension != null) {
              final LanguageLevel level = extension.getLanguageLevel();
              if (level == null && projectLevelIsHigh || level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) {
                highLevelModules.add(module);
              }
            }
          }
          return highLevelModules.isEmpty();
        }
        return true;
      }
    })) {
      return true;
    }
    return collectFunctionalExpressions(aClass, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return queryParameters.getEffectiveSearchScope();
      }
    }), consumer, highLevelModules);
  }

  @NotNull
  private static GlobalSearchScope convertToGlobalScope(Project project, SearchScope useScope) {
    final GlobalSearchScope scope;
    if (useScope instanceof GlobalSearchScope) {
      scope = (GlobalSearchScope)useScope;
    }
    else if (useScope instanceof LocalSearchScope) {
      final Set<VirtualFile> files = new HashSet<VirtualFile>();
      ContainerUtil.addAllNotNull(files, ContainerUtil.map(((LocalSearchScope)useScope).getScope(), new Function<PsiElement, VirtualFile>() {
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

  public static boolean collectFunctionalExpressions(final PsiClass aClass,
                                                     final SearchScope searchScope,
                                                     final Processor<PsiFunctionalExpression> consumer, 
                                                     final Set<Module> highLevelModules) {
    final Project project = PsiUtilCore.getProjectInReadAction(aClass);
    final ModulesScope modulesScope = new ModulesScope(highLevelModules, project);
    final GlobalSearchScope useScope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        return modulesScope.intersectWith(convertToGlobalScope(project, searchScope.intersectWith(aClass.getUseScope())));
      }
    });

    final PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
    final HashSet<VirtualFile> files = new HashSet<VirtualFile>();
    final CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>(files);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "::", processor);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "->", processor);

    final GlobalSearchScope filesScope = GlobalSearchScope.filesScope(project, files);

    final Collection<PsiMethod> lambdaCandidates = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        final String functionalInterfaceName = aClass.getName();
        JavaMethodParameterTypesIndex parameterTypesIndex = JavaMethodParameterTypesIndex.getInstance();
       
        LinkedHashSet<PsiMethod> methods = new LinkedHashSet<PsiMethod>(parameterTypesIndex.get(functionalInterfaceName, project, useScope));
       
        methods.addAll(parameterTypesIndex.get(JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project,
                                               GlobalSearchScope.allScope(project)));
        return methods;
      }
    });
    
    final LinkedHashSet<VirtualFile> usageFiles = new LinkedHashSet<VirtualFile>();
    final MethodSignature functionalInterfaceMethod = ApplicationManager.getApplication().runReadAction(new Computable<MethodSignature>() {
      @Override
      public MethodSignature compute() {
        return LambdaUtil.getFunction(aClass);
      }
    });
    LOG.assertTrue(functionalInterfaceMethod != null);
    final int expectedFunExprParamsCount = functionalInterfaceMethod.getParameterTypes().length;
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    for (final PsiMethod psiMethod : lambdaCandidates) {
      final int parametersCount = psiMethod.getParameterList().getParametersCount();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final boolean varArgs = psiMethod.isVarArgs();
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          final GlobalSearchScope methodUseScope = modulesScope.intersectWith(convertToGlobalScope(project, psiMethod.getUseScope()));
          fileBasedIndex.processValues(JavaFunctionalExpressionIndex.JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID, psiMethod.getName(), null,
                                       new FileBasedIndex.ValueProcessor<Collection<JavaFunctionalExpressionIndex.IndexHolder>>() {
                                         @Override
                                         public boolean process(VirtualFile file, Collection<JavaFunctionalExpressionIndex.IndexHolder> holders) {
                                           for (JavaFunctionalExpressionIndex.IndexHolder holder : holders) {
                                             if (holder.getLambdaParamsNumber() == expectedFunExprParamsCount &&
                                                 (varArgs ? holder.getMethodArgsLength() >= parametersCount - 1 : holder.getMethodArgsLength() == parametersCount) &&
                                                 canBeFunctional(holder)
                                               ) {
                                               usageFiles.add(file);
                                               break;
                                             }
                                           }
                                           return true;
                                         }

                                         private boolean canBeFunctional(JavaFunctionalExpressionIndex.IndexHolder holder) {
                                           final int paramIdx = holder.getFunctionExpressionIndex();
                                           final PsiClass functionalCandidate = PsiUtil.resolveClassInClassTypeOnly(parameters[paramIdx].getType());
                                           return functionalCandidate instanceof PsiTypeParameter ||
                                                  LambdaUtil.isFunctionalClass(functionalCandidate);
                                         }
                                       }, useScope.intersectWith(methodUseScope));
        }
      });
    }

    collectFilesWithAssignments(aClass, filesScope, usageFiles);

    LOG.info("#usage files: " + usageFiles.size());
    return ContainerUtil.process(usageFiles, new ReadActionProcessor<VirtualFile>() {
      @Override
      public boolean processInReadAction(VirtualFile file) {
        return processFileWithFunctionalInterfaces(aClass, expectedFunExprParamsCount, consumer, file);
      }
    });
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
  private static void collectFilesWithAssignments(PsiClass aClass,
                                                  GlobalSearchScope filesScope,
                                                  final LinkedHashSet<VirtualFile> usageFiles) {
    final Set<PsiField> fields = new LinkedHashSet<PsiField>();
    for (final PsiReference reference : ReferencesSearch.search(aClass, filesScope)) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiElement element = reference.getElement();
          if (element != null) {
            ContainerUtil.addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(element));
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
            ContainerUtil.addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(fieldElement));
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
}
