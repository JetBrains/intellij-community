/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.packageDependencies;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class DependencyVisitorFactory {
  private static final LanguageExtension<DependencyVisitorFactory> EP_NAME =
    new LanguageExtension<>("com.intellij.packageDependencies.visitor");

  public static abstract class VisitorOptions {
    public abstract boolean skipImports();

    public static final VisitorOptions SKIP_IMPORTS = new VisitorOptions() {
      @Override
      public boolean skipImports() {
        return true;
      }
    };

    public static final VisitorOptions INCLUDE_IMPORTS = new VisitorOptions() {
      @Override
      public boolean skipImports() {
        return false;
      }
    };

    public static VisitorOptions fromSettings(@NotNull Project project) {
      final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
      return new VisitorOptions() {
        @Override
        public boolean skipImports() {
          return manager.skipImportStatements();
        }
      };
    }
  }

  @NotNull
  public abstract PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor, @NotNull VisitorOptions options);


  @NotNull
  public static PsiElementVisitor createVisitor(@NotNull PsiFile file,
                                                @NotNull DependenciesBuilder.DependencyProcessor processor,
                                                @NotNull VisitorOptions options) {
    DependencyVisitorFactory factory = EP_NAME.forLanguage(file.getLanguage());
    return factory != null ? factory.getVisitor(processor, options) : new DefaultVisitor(processor);
  }

  private static class DefaultVisitor extends PsiRecursiveElementVisitor {
    private final DependenciesBuilder.DependencyProcessor myProcessor;

    public DefaultVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor) {
      myProcessor = processor;
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      for (PsiReference ref : element.getReferences()) {
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }
  }
}
