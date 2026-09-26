// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class JavaBackendFoldings {

  public static void buildBackendFoldRegions(@NotNull JavaFoldingBuilderBase builder,
                                      @NotNull List<FoldingDescriptor> descriptors,
                                      @NotNull PsiJavaFile file,
                                      @NotNull Document document,
                                      boolean quick) {
    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.checkCanceled();
      addFoldsForClass(builder, descriptors, aClass, document, quick);
    }
  }

  private static void addFoldsForClass(@NotNull JavaFoldingBuilderBase builder,
                                       @NotNull List<? super FoldingDescriptor> list,
                                       @NotNull PsiClass aClass,
                                       @NotNull Document document,
                                       boolean quick) {
    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      ProgressIndicatorProvider.checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addFoldsForMethod(builder, list, method, document, quick);
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;

        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          addCodeBlockFolds(builder, list, initializer, document, quick);
        }
        else if (field instanceof PsiEnumConstant) {
          addCodeBlockFolds(builder, list, field, document, quick);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        addCodeBlockFolds(builder, list, child, document, quick);
      }
      else if (child instanceof PsiClass) {
        addFoldsForClass(builder, list, (PsiClass)child, document, quick);
      }
    }
  }

  private static void addFoldsForMethod(@NotNull JavaFoldingBuilderBase builder,
                                        @NotNull List<? super FoldingDescriptor> list,
                                        @NotNull PsiMethod method,
                                        @NotNull Document document,
                                        boolean quick) {
    boolean oneLiner = addOneLineMethodFolding(builder, list, method);
    if (!oneLiner) {
      boolean collapseMethodByDefault = isCollapseMethodByDefault(method);
      JavaFoldingUtil.addToFold(list, method, document, true, JavaFoldingUtil.getCodeBlockPlaceholder(method.getBody()),
                                JavaFoldingUtil.methodRange(method),
                                collapseMethodByDefault);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      addCodeBlockFolds(builder, list, body, document, quick);
    }
  }

  private static boolean addOneLineMethodFolding(@NotNull JavaFoldingBuilderBase builder,
                                                 @NotNull List<? super FoldingDescriptor> list,
                                                 @NotNull PsiMethod method) {
    boolean collapseOneLineMethods = JavaCodeFoldingSettings.getInstance().isCollapseOneLineMethods();
    if (!collapseOneLineMethods) {
      return false;
    }

    Document document = method.getContainingFile().getViewProvider().getDocument();
    PsiCodeBlock body = method.getBody();
    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    if (body == null || document == null || nameIdentifier == null) {
      return false;
    }
    TextRange parameterListTextRange = method.getParameterList().getTextRange();
    if (parameterListTextRange == null || document.getLineNumber(nameIdentifier.getTextRange().getStartOffset()) !=
                                          document.getLineNumber(parameterListTextRange.getEndOffset())) {
      return false;
    }

    PsiJavaToken lBrace = body.getLBrace();
    PsiJavaToken rBrace = body.getRBrace();
    PsiStatement[] statements = body.getStatements();
    if (lBrace == null || rBrace == null || statements.length != 1) {
      return false;
    }

    PsiStatement statement = statements[0];
    if (statement.textContains('\n')) {
      return false;
    }

    if (!JavaFoldingUtil.areOnAdjacentLines(lBrace, statement, document) ||
        !JavaFoldingUtil.areOnAdjacentLines(statement, rBrace, document)) {
      //the user might intend to type at an empty line
      return false;
    }

    int leftStart = parameterListTextRange.getEndOffset();
    int bodyStart = body.getTextRange().getStartOffset();
    if (bodyStart > leftStart && !StringUtil.isEmptyOrSpaces(document.getCharsSequence().subSequence(leftStart + 1, bodyStart))) {
      return false;
    }

    int leftEnd = statement.getTextRange().getStartOffset();
    int rightStart = statement.getTextRange().getEndOffset();
    int rightEnd = body.getTextRange().getEndOffset();
    if (leftEnd <= leftStart + 1 || rightEnd <= rightStart + 1) {
      return false;
    }

    String leftText = " { ";
    String rightText = " }";
    if (!builder.fitsRightMargin(method, document, leftStart, rightEnd, rightStart - leftEnd + leftText.length() + rightText.length())) {
      return false;
    }

    FoldingGroup group = FoldingGroup.newGroup("one-liner");
    list.add(new FoldingDescriptor(lBrace.getNode(), new TextRange(leftStart, leftEnd), group, leftText, true, Collections.emptySet()));
    list.add(new FoldingDescriptor(rBrace.getNode(), new TextRange(rightStart, rightEnd), group, rightText, true, Collections.emptySet()));
    return true;
  }

  private static void addCodeBlockFolds(@NotNull JavaFoldingBuilderBase builder,
                                        @NotNull List<? super FoldingDescriptor> list,
                                        @NotNull PsiElement scope,
                                        @NotNull Document document,
                                        boolean quick) {
    final boolean dumb = DumbService.isDumb(scope.getProject());
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass) {
          if ((dumb || !addClosureFolding(builder, aClass, document, list, quick))) {
            addFoldsForClass(builder, list, aClass, document, quick);
            JavaFrontendFoldings.addFrontendFoldsForClass(list, aClass, document);
          }
        }
        else {
          addFoldsForClass(builder, list, aClass, document, quick);
        }
      }

      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        if (!dumb && JavaCodeFoldingSettings.getInstance().isReplaceVarWithInferredType()) {
          addLocalVariableTypeFolding(list, variable, quick);
        }

        super.visitVariable(variable);
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        if (!dumb) {
          addMethodGenericParametersFolding(list, expression, document, quick);
        }

        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (!dumb) {
          addGenericParametersFolding(list, expression, document, quick);
        }

        super.visitNewExpression(expression);
      }
    });
  }

  private static boolean addClosureFolding(@NotNull JavaFoldingBuilderBase builder,
                                           @NotNull PsiClass aClass,
                                           @NotNull Document document,
                                           @NotNull List<? super FoldingDescriptor> list,
                                           boolean quick) {
    if (!JavaCodeFoldingSettings.getInstance().isCollapseLambdas()) {
      return false;
    }

    if (aClass instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      BackendClosureFolding backendClosureFolding = BackendClosureFolding.prepare(anonymousClass, quick, builder);
      List<FoldingDescriptor> descriptors = backendClosureFolding == null ? null : backendClosureFolding.process(document);
      if (descriptors != null) {
        list.addAll(descriptors);
        addCodeBlockFolds(builder, list, backendClosureFolding.methodBody, document, quick);
        JavaFrontendFoldings.addFrontendCodeBlockFolds(list, backendClosureFolding.methodBody, document);
        return true;
      }
    }
    return false;
  }

  private static boolean isSimplePropertyAccessor(@NotNull PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) return false;

    PsiCodeBlock body = method.getBody();
    if (body == null || body.getLBrace() == null || body.getRBrace() == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) return false;

    PsiStatement statement = statements[0];
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        return ((PsiReturnStatement)statement).getReturnValue() instanceof PsiReferenceExpression;
      }
      return false;
    }

    // builder-style setter?
    if (statements.length > 1 && !(statements[1] instanceof PsiReturnStatement)) return false;

    // any setter?
    if (statement instanceof PsiExpressionStatement) {
      PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
      if (expr instanceof PsiAssignmentExpression) {
        PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
        PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
        return lhs instanceof PsiReferenceExpression &&
               rhs instanceof PsiReferenceExpression &&
               !((PsiReferenceExpression)rhs).isQualified() &&
               PropertyUtilBase.isSimplePropertySetter(method); // last check because it can perform long return type resolve
      }
    }
    return false;
  }

  private static void addMethodGenericParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                                        @NotNull PsiMethodCallExpression expression,
                                                        @NotNull Document document,
                                                        boolean quick) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
    if (parameterList == null || parameterList.getTextLength() <= 5) {
      return;
    }

    PsiMethodCallExpression element = expression;
    while (true) {
      if (!quick && !resolvesCorrectly(element.getMethodExpression())) return;
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionList) || !(parent.getParent() instanceof PsiMethodCallExpression)) break;
      element = (PsiMethodCallExpression)parent.getParent();
    }

    addTypeParametersFolding(list, document, parameterList, 3, quick);
  }

  private static void addLocalVariableTypeFolding(@NotNull List<? super FoldingDescriptor> list,
                                                  @NotNull PsiVariable expression,
                                                  boolean quick) {
    if (quick) return; // presentable text may require resolve
    PsiTypeElement typeElement = expression.getTypeElement();
    if (typeElement == null) return;
    if (!typeElement.isInferredType()) return;
    PsiType type = expression.getType();
    if (type instanceof PsiCapturedWildcardType || type.equals(PsiTypes.nullType())) return;
    String presentableText = type.getPresentableText();
    if (presentableText.length() > 25) return;
    list.add(new FoldingDescriptor(typeElement.getNode(), typeElement.getTextRange(), null, presentableText, true, Collections.emptySet()));
  }

  private static boolean resolvesCorrectly(@NotNull PsiReferenceExpression expression) {
    for (final JavaResolveResult result : expression.multiResolve(true)) {
      if (!result.isValidResult()) {
        return false;
      }
    }
    return true;
  }

  private static void addGenericParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                                  @NotNull PsiNewExpression expression,
                                                  @NotNull Document document,
                                                  boolean quick) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiVariable)) {
      return;
    }

    final PsiType declType = ((PsiVariable)parent).getType();
    if (!(declType instanceof PsiClassReferenceType)) {
      return;
    }

    final PsiType[] parameters = ((PsiClassType)declType).getParameters();
    if (parameters.length == 0) {
      return;
    }

    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        classReference = anonymousClass.getBaseClassReference();

        if (quick || BackendClosureFolding.seemsLikeLambda(anonymousClass.getSuperClass(), anonymousClass)) {
          return;
        }
      }
    }

    if (classReference != null) {
      final PsiReferenceParameterList parameterList = classReference.getParameterList();
      if (parameterList != null) {
        if (quick) {
          final PsiJavaCodeReferenceElement declReference = ((PsiClassReferenceType)declType).getReference();
          final PsiReferenceParameterList declList = declReference.getParameterList();
          if (declList == null || !parameterList.getText().equals(declList.getText())) {
            return;
          }
        }
        else if (!Arrays.equals(parameterList.getTypeArguments(), parameters)) {
          return;
        }

        addTypeParametersFolding(list, document, parameterList, 5, quick);
      }
    }
  }

  private static void addTypeParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                               @NotNull Document document,
                                               @NotNull PsiReferenceParameterList parameterList,
                                               int ifLongerThan,
                                               boolean quick) {
    if (!quick) {
      for (final PsiType type : parameterList.getTypeArguments()) {
        if (!type.isValid()) {
          return;
        }
        if (type instanceof PsiClassType || type instanceof PsiArrayType) {
          if (PsiUtil.resolveClassInType(type) == null) {
            return;
          }
        }
      }
    }

    final String text = parameterList.getText();
    if (text.startsWith("<") && text.endsWith(">") && text.length() > ifLongerThan) {
      final TextRange range = parameterList.getTextRange();
      JavaFoldingUtil.addFoldRegion(list, parameterList, document, true, range, "<~>",
                                    JavaCodeFoldingSettings.getInstance().isCollapseConstructorGenericParameters());
    }
  }

  private static boolean isCollapseMethodByDefault(@NotNull PsiMethod element) {
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    if (!settings.isCollapseAccessors() && !settings.isCollapseMethods()) {
      return false;
    }

    if (isSimplePropertyAccessor(element)) {
      return settings.isCollapseAccessors();
    }
    return settings.isCollapseMethods();
  }
}
