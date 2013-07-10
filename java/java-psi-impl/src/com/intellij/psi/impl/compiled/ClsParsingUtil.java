/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsParsingUtil");

  private static final JavaParserUtil.ParserWrapper ANNOTATION_VALUE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotationValue(builder);
    }
  };

  private ClsParsingUtil() { }

  public static PsiExpression createExpressionFromText(final String exprText, final PsiManager manager, final ClsElementImpl parent) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();
    final PsiJavaFile dummyJavaFile = ((PsiJavaParserFacadeImpl)parserFacade).getDummyJavaFile(); // to resolve classes from java.lang
    final PsiExpression expr;
    try {
      expr = parserFacade.createExpressionFromText(exprText, dummyJavaFile);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }

    return psiToClsExpression(expr, parent);
  }

  @NotNull
  public static PsiAnnotationMemberValue createMemberValueFromText(final String text, final PsiManager manager, final ClsElementImpl parent) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiJavaFile context = ((PsiJavaParserFacadeImpl)factory).getDummyJavaFile(); // to resolve classes from java.lang
    final LanguageLevel level = PsiUtil.getLanguageLevel(parent);
    final DummyHolder holder = DummyHolderFactory.createHolder(manager, new JavaDummyElement(text, ANNOTATION_VALUE, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotationMemberValue)) {
      LOG.error("Could not parse initializer:'" + text + "'");
      return null;
    }

    return getMemberValue(element, parent);
  }

  @NotNull
  public static PsiAnnotationMemberValue getMemberValue(final PsiElement element, final ClsElementImpl parent) {
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

  @NotNull
  private static PsiExpression psiToClsExpression(final PsiExpression expr, @Nullable final ClsElementImpl parent) {
    if (expr instanceof PsiLiteralExpression) {
      return new ClsLiteralExpressionImpl(parent, expr.getText(), expr.getType(), ((PsiLiteralExpression)expr).getValue());
    }
    if (expr instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpr = (PsiPrefixExpression)expr;
      final ClsJavaTokenImpl operation = new ClsJavaTokenImpl(null, prefixExpr.getOperationTokenType(), prefixExpr.getOperationSign().getText());
      final ClsLiteralExpressionImpl literal = (ClsLiteralExpressionImpl) psiToClsExpression(prefixExpr.getOperand(), null);
      return new ClsPrefixExpressionImpl(parent, operation, literal);
    }
    if (expr instanceof PsiClassObjectAccessExpression) {
      final String canonicalClassText = ((PsiClassObjectAccessExpression)expr).getOperand().getType().getCanonicalText();
      return new ClsClassObjectAccessExpressionImpl(parent, canonicalClassText);
    }
    if (expr instanceof PsiReferenceExpression) {
      return new ClsReferenceExpressionImpl(parent, (PsiReferenceExpression)expr);
    }
    if (expr instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpr = (PsiBinaryExpression)expr;
      final PsiExpression lOperand = psiToClsExpression(binaryExpr.getLOperand(), null);
      final ClsJavaTokenImpl operation = new ClsJavaTokenImpl(null, binaryExpr.getOperationTokenType(), binaryExpr.getOperationSign().getText());
      final PsiExpression rOperand = psiToClsExpression(binaryExpr.getROperand(), null);
      if (lOperand instanceof ClsLiteralExpressionImpl) {
        return new ClsBinaryExpressionImpl(parent, (ClsLiteralExpressionImpl)lOperand, operation, (ClsLiteralExpressionImpl)rOperand);
      }
      else if (lOperand instanceof ClsPrefixExpressionImpl) {
        return new ClsBinaryExpressionImpl(parent, (ClsPrefixExpressionImpl)lOperand, operation, (ClsLiteralExpressionImpl)rOperand);
      }
    }
    else {
      final PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper();
      final Object value = evaluator.computeConstantExpression(expr);
      if (value != null) {
        return new ClsLiteralExpressionImpl(parent, expr.getText(), expr.getType(), value);
      }
    }

    LOG.error("Unable to compute expression value: " + expr);
    return null;
  }

  public static boolean isJavaIdentifier(@NotNull String identifier, @NotNull LanguageLevel level) {
    return StringUtil.isJavaIdentifier(identifier) && !JavaLexer.isKeyword(identifier, level);
  }
}
