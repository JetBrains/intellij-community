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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

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

  public static boolean collectFunctionalExpressions(final PsiClass aClass,
                                                     final SearchScope searchScope,
                                                     final Processor<PsiFunctionalExpression> consumer, 
                                                     final Set<Module> highLevelModules) {
    final Project project = PsiUtilCore.getProjectInReadAction(aClass);
    final GlobalSearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        return prepareScopeToProcessFiles(highLevelModules, aClass, searchScope, project);
      }
    });
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final HashSet<VirtualFile> files = new HashSet<VirtualFile>();
    CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>(files) {
      @Override
      protected boolean accept(VirtualFile virtualFile) {
        return scope.contains(virtualFile) && virtualFile.getFileType() == JavaFileType.INSTANCE && index.isInSource(virtualFile);
      }
    };

    final PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
    helper.processFilesWithText(scope, UsageSearchContext.IN_CODE, true, "::", processor);
    helper.processFilesWithText(scope, UsageSearchContext.IN_CODE, true, "->", processor);
    LOG.info("#files: " + files.size());

    final PsiManager psiManager = PsiManager.getInstance(project);
    for (final VirtualFile file : files) {
      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return processFileWithFunctionalInterfaces(aClass, consumer, psiManager, file);
        }
      })) return false;
    }
    return true;
  }

  @NotNull
  protected static GlobalSearchScope prepareScopeToProcessFiles(final Set<Module> highLevelModules,
                                                                final PsiClass aClass,
                                                                final SearchScope searchScope,
                                                                final Project project) {
    final SearchScope useScope = searchScope.intersectWith(aClass.getUseScope());
      
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
      scope = new ModulesScope(highLevelModules, project).intersectWith(new EverythingGlobalScope(project));
    }

    return new ModulesScope(highLevelModules, project).intersectWith(scope);
  }

  private static boolean processFileWithFunctionalInterfaces(final PsiClass aClass,
                                                             final Processor<PsiFunctionalExpression> consumer,
                                                             final PsiManager psiManager, VirtualFile file) {
    final PsiFile psiFile = psiManager.findFile(file);
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
          visitFunctionalExpression(expression);
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
