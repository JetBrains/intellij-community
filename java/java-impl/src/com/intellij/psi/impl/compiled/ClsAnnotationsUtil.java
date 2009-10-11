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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsAnnotationsUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationsUtil");

  private ClsAnnotationsUtil() {}

  @NotNull public static PsiAnnotationMemberValue getMemberValue(PsiElement element, ClsElementImpl parent) {
    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression expr = (PsiLiteralExpression)element;
      return new ClsLiteralExpressionImpl(parent, element.getText(), expr.getType(), expr.getValue());
    }
    else if (element instanceof PsiPrefixExpression) {
      PsiExpression operand = ((PsiPrefixExpression) element).getOperand();
      ClsLiteralExpressionImpl literal = (ClsLiteralExpressionImpl) getMemberValue(operand, null);
      ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literal);
      literal.setParent(prefixExpression);
      return prefixExpression;
    }
    else if (element instanceof PsiClassObjectAccessExpression) {
      PsiClassObjectAccessExpression expr = (PsiClassObjectAccessExpression)element;
      return new ClsClassObjectAccessExpressionImpl(expr.getOperand().getType().getCanonicalText(), parent);
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
      final String canonicalText = psiAnnotation.getNameReferenceElement().getCanonicalText();
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
    else if (element instanceof PsiReferenceExpression) {
      return new ClsReferenceExpressionImpl(parent, (PsiReferenceExpression)element);
    }
    else {
      LOG.error("Unexpected source element for annotation member value: " + element);
      return null;
    }
  }

  @NotNull public static PsiAnnotationMemberValue createMemberValueFromText(String text, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, dummyJavaFile).getTreeElement();
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(parent);
    JavaParsingContext context = new JavaParsingContext(holderElement.getCharTable(), languageLevel);
    TreeElement element = context.getDeclarationParsing().parseMemberValueText(manager, text, languageLevel);
    if (element == null) {
      LOG.error("Could not parse initializer:'" + text + "'");
      return null;
    }
    holderElement.rawAddChildren(element);
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    return getMemberValue(psiElement, parent);
  }
}
