// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordHeader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JavaFrontendFoldings {
  public static void buildFrontendFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                       @NotNull PsiJavaFile file,
                                       @NotNull Document document) {
    JavaFoldingUtil.addFoldsForImports(descriptors, file);

    PsiJavaModule module = file.getModuleDeclaration();
    if (module != null) {
      JavaFoldingUtil.addFoldsForModule(descriptors, module, document);
    }

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.checkCanceled();
      addFrontendFoldsForClass(descriptors, aClass, document);
    }

    JavaFoldingUtil.addFoldsForFileHeader(descriptors, file, document);
    JavaFoldingUtil.addAllCommentsToFold(descriptors, file, document);
  }

  static void addFrontendFoldsForClass(@NotNull List<? super FoldingDescriptor> list,
                                       @NotNull PsiClass aClass,
                                       @NotNull Document document) {
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiJavaFile) || ((PsiJavaFile)parent).getClasses().length > 1) {
      JavaFoldingUtil.addToFold(list, aClass, document, true, JavaFoldingUtil.getCodeBlockPlaceholder(null),
                                JavaFoldingUtil.classRange(aClass),
                                !(parent instanceof PsiFile) && JavaCodeFoldingSettings.getInstance().isCollapseInnerClasses());
    }

    JavaFoldingUtil.addAnnotationsToFold(list, aClass.getModifierList(), document);

    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      ProgressIndicatorProvider.checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addFrontendFoldsForMethod(list, method, document);
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;

        JavaFoldingUtil.addAnnotationsToFold(list, field.getModifierList(), document);

        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          addFrontendCodeBlockFolds(list, initializer, document);
        }
        else if (field instanceof PsiEnumConstant) {
          addFrontendCodeBlockFolds(list, field, document);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        JavaFoldingUtil.addToFold(list, child, document, true, JavaFoldingUtil.getCodeBlockPlaceholder(initializer.getBody()),
                                  initializer.getBody().getTextRange(),
                                  JavaCodeFoldingSettings.getInstance().isCollapseMethods());
        addFrontendCodeBlockFolds(list, child, document);
      }
      else if (child instanceof PsiClass) {
        addFrontendFoldsForClass(list, (PsiClass)child, document);
      }
      else if (child instanceof PsiRecordHeader) {
        JavaFoldingUtil.addToFold(list, child, document, false, "(...)", child.getTextRange(), false);
      }
    }
  }

  private static void addFrontendFoldsForMethod(@NotNull List<? super FoldingDescriptor> list,
                                                @NotNull PsiMethod method,
                                                @NotNull Document document) {
    JavaFoldingUtil.addAnnotationsToFold(list, method.getModifierList(), document);

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      JavaFoldingUtil.addAnnotationsToFold(list, parameter.getModifierList(), document);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      addFrontendCodeBlockFolds(list, body, document);
    }
  }

  static void addFrontendCodeBlockFolds(final @NotNull List<? super FoldingDescriptor> list, @NotNull PsiElement scope,
                                        final @NotNull Document document) {
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass) {
          //will be processed by backend
          return;
        }
        JavaFoldingUtil.addToFold(list, aClass, document, true, JavaFoldingUtil.getCodeBlockPlaceholder(null),
                                  JavaFoldingUtil.classRange(aClass),
                                  JavaCodeFoldingSettings.getInstance().isCollapseInnerClasses());
        addFrontendFoldsForClass(list, aClass, document);
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          JavaFoldingUtil.addToFold(list, expression, document, true, JavaFoldingUtil.getCodeBlockPlaceholder(expression.getBody()),
                                    JavaFoldingUtil.lambdaRange(expression),
                                    JavaCodeFoldingSettings.getInstance().isCollapseAnonymousClasses());
        }
        super.visitLambdaExpression(expression);
      }

      @Override
      public void visitCodeBlock(@NotNull PsiCodeBlock block) {
        if (Registry.is("java.folding.icons.for.control.flow", true) && block.getStatementCount() > 0) {
          PsiElement parent = block.getParent();
          // Method bodies and class initializer bodies are folded by the backend (with correct isCollapsedByDefault)
          if (!(parent instanceof PsiMethod) && !(parent instanceof PsiClassInitializer)) {
            JavaFoldingUtil.addToFold(list, block, document, false, JavaFoldingUtil.getCodeBlockPlaceholder(block), block.getTextRange(),
                                      false);
          }
        }
        super.visitCodeBlock(block);
      }
    });
  }
}
