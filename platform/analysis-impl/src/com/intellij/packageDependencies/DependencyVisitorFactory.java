// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class DependencyVisitorFactory {
  private static final LanguageExtension<DependencyVisitorFactory> EP_NAME =
    new LanguageExtension<>("com.intellij.packageDependencies.visitor");

  public abstract static class VisitorOptions {
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

  public abstract @NotNull PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor, @NotNull VisitorOptions options);


  public static @NotNull PsiElementVisitor createVisitor(@NotNull PsiFile file,
                                                         @NotNull DependenciesBuilder.DependencyProcessor processor,
                                                         @NotNull VisitorOptions options) {
    DependencyVisitorFactory factory = EP_NAME.forLanguage(file.getLanguage());
    return factory != null ? factory.getVisitor(processor, options) : new DefaultVisitor(processor);
  }

  private static class DefaultVisitor extends PsiRecursiveElementVisitor {
    private final DependenciesBuilder.DependencyProcessor myProcessor;

    DefaultVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor) {
      myProcessor = processor;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
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
