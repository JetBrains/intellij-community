/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
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
      DeclarationParser.parseAnnotationValue(builder);
    }
  };

  private ClsParsingUtil() { }

  public static PsiExpression createExpressionFromText(final String exprText, final PsiManager manager, final ClsElementImpl parent) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();
    final PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)parserFacade).getDummyJavaFile(); // to resolve classes from java.lang
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
    final PsiJavaFile context = ((PsiElementFactoryImpl)factory).getDummyJavaFile(); // to resolve classes from java.lang
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
      final String canonicalText = referenceElement.getCanonicalText();
      return new ClsAnnotationValueImpl(parent) {
        protected ClsJavaCodeReferenceElementImpl createReference() {
          return new ClsJavaCodeReferenceElementImpl(this, canonicalText);
        }

        protected ClsAnnotationParameterListImpl createParameterList() {
          PsiNameValuePair[] psiAttributes = psiAnnotation.getParameterList().getAttributes();
          return new ClsAnnotationParameterListImpl(this, psiAttributes);
        }

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
    else if (expr instanceof PsiPrefixExpression) {
      final PsiExpression operand = ((PsiPrefixExpression) expr).getOperand();
      final ClsLiteralExpressionImpl literal = (ClsLiteralExpressionImpl) psiToClsExpression(operand, null);
      final ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literal);
      literal.setParent(prefixExpression);
      return prefixExpression;
    }
    else if (expr instanceof PsiClassObjectAccessExpression) {
      final String canonicalClassText = ((PsiClassObjectAccessExpression)expr).getOperand().getType().getCanonicalText();
      return new ClsClassObjectAccessExpressionImpl(canonicalClassText, parent);
    }
    else if (expr instanceof PsiReferenceExpression) {
      return new ClsReferenceExpressionImpl(parent, (PsiReferenceExpression)expr);
    }
    else {
      final PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper();
      final Object value = evaluator.computeConstantExpression(expr);
      if (value != null) {
        return new ClsLiteralExpressionImpl(parent, expr.getText(), expr.getType(), value);
      }
      LOG.error("Unable to compute expression value: " + expr);
      return null;
    }
  }
}
