// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance(ClsParsingUtil.class);

  private static final JavaParserUtil.ParserWrapper ANNOTATION_VALUE =
    (builder, languageLevel) -> new JavaParser(languageLevel).getDeclarationParser().parseAnnotationValue(builder);

  private ClsParsingUtil() { }

  public static PsiExpression createExpressionFromText(@NotNull String exprText,
                                                       @NotNull PsiManager manager,
                                                       @NotNull ClsElementImpl parent) {
    PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();
    try {
      PsiExpression expr = parserFacade.createExpressionFromText(exprText, null);
      return psiToClsExpression(expr, parent);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static PsiAnnotationMemberValue createMemberValueFromText(@NotNull String text,
                                                                   @NotNull PsiManager manager,
                                                                   @NotNull ClsElementImpl parent) {
    LanguageLevel level = PsiUtil.getLanguageLevel(parent);
    DummyHolder holder = DummyHolderFactory.createHolder(manager, new JavaDummyElement(text, ANNOTATION_VALUE, level), null);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotationMemberValue)) {
      LOG.error("Could not parse initializer:'" + text + "'");
      return null;
    }
    return getMemberValue(element, parent);
  }

  public static PsiAnnotationMemberValue getMemberValue(@NotNull PsiElement element, @NotNull ClsElementImpl parent) {
    if (element instanceof PsiExpression) {
      return psiToClsExpression((PsiExpression)element, parent);
    }
    else if (element instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)element).getInitializers();
      PsiAnnotationMemberValue[] clsInitializers = new PsiAnnotationMemberValue[initializers.length];
      ClsArrayInitializerMemberValueImpl arrayValue = new ClsArrayInitializerMemberValueImpl(parent, clsInitializers);
      for (int i = 0; i < initializers.length; i++) {
        clsInitializers[i] = getMemberValue(initializers[i], arrayValue);
      }
      return arrayValue;
    }
    else if (element instanceof PsiAnnotation) {
      final PsiAnnotation psiAnnotation = (PsiAnnotation)element;
      final PsiJavaCodeReferenceElement referenceElement = psiAnnotation.getNameReferenceElement();
      assert referenceElement != null : psiAnnotation;
      final String canonicalText = referenceElement.getText(); // class file has FQNs
      return new ClsAnnotationValueImpl(parent) {
        @Override
        protected ClsJavaCodeReferenceElementImpl createReference() {
          return new ClsJavaCodeReferenceElementImpl(this, canonicalText);
        }

        @Override
        protected ClsAnnotationParameterListImpl createParameterList() {
          PsiNameValuePair[] psiAttributes = psiAnnotation.getParameterList().getAttributes();
          return new ClsAnnotationParameterListImpl(this, psiAttributes);
        }

        @Override
        public PsiAnnotationOwner getOwner() {
          return (PsiAnnotationOwner)getParent();
        }
      };
    }
    else {
      LOG.error("Unexpected source element for annotation member value: " + element);
      return null;
    }
  }

  static PsiExpression psiToClsExpression(@NotNull PsiExpression expr, @NotNull ClsElementImpl parent) {
    if (expr instanceof PsiLiteralExpression) {
      PsiFile file = parent.getContainingFile();
      boolean forDecompiling = file instanceof ClsFileImpl && ((ClsFileImpl)file).isForDecompiling();
      PsiType type = forDecompiling ? (PsiPrimitiveType)PsiTypes.nullType() : expr.getType();
      Object value = forDecompiling ? null : ((PsiLiteralExpression)expr).getValue();
      return new ClsLiteralExpressionImpl(parent, expr.getText(), type, value);
    }

    if (expr instanceof PsiPrefixExpression) {
      PsiJavaToken sign = ((PsiPrefixExpression)expr).getOperationSign();
      PsiExpression operand = ((PsiPrefixExpression)expr).getOperand();
      if (operand == null) {
        LOG.error("Invalid prefix expression: " + expr + " [" + expr.getText() + "]");
        return null;
      }
      return new ClsPrefixExpressionImpl(parent, sign, operand);
    }

    if (expr instanceof PsiClassObjectAccessExpression) {
      String exprText = expr.getText();
      if (StringUtil.endsWith(exprText, ".class")) {
        String classText = exprText.substring(0, exprText.length() - 6);
        return new ClsClassObjectAccessExpressionImpl(parent, classText);
      }
    }

    if (expr instanceof PsiReferenceExpression) {
      return new ClsReferenceExpressionImpl(parent, (PsiReferenceExpression)expr);
    }

    if (expr instanceof PsiBinaryExpression) {
      PsiJavaToken sign = ((PsiBinaryExpression)expr).getOperationSign();
      PsiExpression left = ((PsiBinaryExpression)expr).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)expr).getROperand();
      if (right == null) {
        LOG.error("Invalid binary expression: " + expr + " [" + expr.getText() + "]");
        return null;
      }
      return new ClsBinaryExpressionImpl(parent, sign, left, right);
    }

    PsiFile file = parent.getContainingFile();
    if (file instanceof ClsFileImpl && ((ClsFileImpl)file).isForDecompiling()) {
      return new ClsLiteralExpressionImpl(parent, expr.getText(), PsiTypes.nullType(), null);
    }

    PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper();
    Object value = evaluator.computeConstantExpression(expr);
    if (value != null) {
      return new ClsLiteralExpressionImpl(parent, expr.getText(), expr.getType(), value);
    }

    LOG.error("Unable to compute expression value: " + expr + " [" + expr.getText() + "]");
    return null;
  }

  public static boolean isJavaIdentifier(@NotNull String identifier, @NotNull LanguageLevel level) {
    return StringUtil.isJavaIdentifier(identifier) && !PsiUtil.isKeyword(identifier, level);
  }

  // expecting the parameter in the "unsigned short" format
  public static @Nullable JavaSdkVersion getJdkVersionByBytecode(int major) {
    if (major >= 44) {
      JavaVersion version = JavaVersion.compose(major - 44);  // 44 = 1.0, 45 = 1.1, 46 = 1.2 etc.
      return JavaSdkVersion.fromJavaVersion(version);
    }
    return null;
  }

  // expecting the parameter in the "unsigned short" format
  public static boolean isPreviewLevel(int minor) {
    return minor == 0xFFFF;
  }
}
