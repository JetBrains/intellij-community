/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.aspects.psi.PsiTypePattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

import org.jetbrains.annotations.NonNls;

/**
 *
 */
public interface PsiElementFactory {
  PsiClass createClass(String name) throws IncorrectOperationException;
  PsiClass createInterface(String name) throws IncorrectOperationException;
  PsiField createField(String name, PsiType type) throws IncorrectOperationException;
  PsiMethod createMethod(String name, PsiType returnType) throws IncorrectOperationException;
  PsiMethod createConstructor();
  PsiClassInitializer createClassInitializer() throws IncorrectOperationException;
  PsiParameter createParameter(String name, PsiType type) throws IncorrectOperationException;
  PsiCodeBlock createCodeBlock();

  PsiClassType createType(PsiClass aClass);
  PsiClassType createType(PsiJavaCodeReferenceElement classReference);

  /**
   * Detaches type from reference(s) or type elements it was created from.
   * Optimization method, do not use if you do not understand what does it do.
   * @param type
   * @return
   */
  PsiType detachType(PsiType type);

  PsiSubstitutor createRawSubstitutor(PsiClass aClass);
  PsiSubstitutor createSubstitutor(Map<PsiTypeParameter,PsiType> map);

  PsiPrimitiveType createPrimitiveType(String text);

  /**
   * @deprecated use {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   */
  PsiClassType createTypeByFQClassName(String qName);
  PsiClassType createTypeByFQClassName(String qName, GlobalSearchScope resolveScope);

  PsiTypeElement createTypeElement(PsiType psiType);

  PsiJavaCodeReferenceElement createReferenceElementByType(PsiClassType type);
  PsiJavaCodeReferenceElement createClassReferenceElement(PsiClass aClass);

  PsiJavaCodeReferenceElement createReferenceElementByFQClassName(String qName, GlobalSearchScope resolveScope);

  PsiJavaCodeReferenceElement createFQClassNameReferenceElement(String qName, GlobalSearchScope resolveScope);
  PsiJavaCodeReferenceElement createPackageReferenceElement(PsiPackage aPackage) throws IncorrectOperationException;
  PsiJavaCodeReferenceElement createPackageReferenceElement(String packageName) throws IncorrectOperationException;
  PsiReferenceExpression createReferenceExpression(PsiClass aClass) throws IncorrectOperationException;
  PsiReferenceExpression createReferenceExpression(PsiPackage aPackage) throws IncorrectOperationException;

  PsiIdentifier createIdentifier(String text) throws IncorrectOperationException;
  PsiKeyword createKeyword(String keyword) throws IncorrectOperationException;

  PsiImportStatement createImportStatement(PsiClass aClass) throws IncorrectOperationException;
  PsiImportStatement createImportStatementOnDemand(String packageName) throws IncorrectOperationException;

  PsiDeclarationStatement createVariableDeclarationStatement(String name, PsiType type, PsiExpression initializer)
    throws IncorrectOperationException;

  PsiDeclarationStatement createVariableDeclarationStatement(String name, PsiType type, PsiExpression initializer, boolean reformat)
    throws IncorrectOperationException;

  PsiDocTag createParamTag(String parameterName, String description) throws IncorrectOperationException;
  PsiDocTag createDocTagFromText(String docTagText, PsiElement context) throws IncorrectOperationException;
  PsiDocComment createDocCommentFromText(String docCommentText, PsiElement context) throws IncorrectOperationException;

  PsiFile createFileFromText(String name, String text) throws IncorrectOperationException;
  PsiClass createClassFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiField createFieldFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiMethod createMethodFromText(String text, PsiElement context, LanguageLevel languageLevel) throws IncorrectOperationException;
  PsiMethod createMethodFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiParameter createParameterFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiType createTypeFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiCodeBlock createCodeBlockFromText(@NonNls String text, PsiElement context) throws IncorrectOperationException;
  PsiStatement createStatementFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiExpression createExpressionFromText(String text, PsiElement context) throws IncorrectOperationException;
  PsiComment createCommentFromText(String text, PsiElement context) throws IncorrectOperationException;

  XmlTag createTagFromText(String text) throws IncorrectOperationException;
  XmlAttribute createXmlAttribute(String name,String value) throws IncorrectOperationException;

  PsiTypePattern createTypePattern(String pattern) throws IncorrectOperationException;

  PsiExpressionCodeFragment createExpressionCodeFragment(String text, PsiElement context, final PsiType expectedType, boolean isPhysical);
  PsiCodeFragment createCodeBlockCodeFragment(String text, PsiElement context, boolean isPhysical);
  PsiTypeCodeFragment createTypeCodeFragment(String text, PsiElement context, boolean isPhysical);
  PsiTypeCodeFragment createTypeCodeFragment(String text, PsiElement context, boolean isVoidValid, boolean isPhysical);

  PsiClass getArrayClass();
  PsiClassType getArrayClassType(PsiType componentType);

  PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor);
  PsiTypeParameter createTypeParameterFromText(String text, PsiElement context) throws IncorrectOperationException;

  PsiPackageStatement createPackageStatement(String name) throws IncorrectOperationException;

  XmlTag getAntImplicitDeclarationTag() throws IncorrectOperationException;

  PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(String text,
                                                               PsiElement context,
                                                               boolean isPhysical,
                                                               boolean isClassesAccepted);

  PsiTypeCodeFragment createTypeCodeFragment(String text,
                                             PsiElement context,
                                             boolean isVoidValid,
                                             boolean isPhysical, boolean allowEllipsis);

  PsiAnnotation createAnnotationFromText(String annotationText, PsiElement context) throws IncorrectOperationException;

  PsiImportStaticStatement createImportStaticStatement(PsiClass aClass, String memberName) throws IncorrectOperationException;

  PsiParameterList createParameterList(String[] names, PsiType[] types) throws IncorrectOperationException;

  PsiReferenceList createReferenceList(PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException;

  PsiCatchSection createCatchSection (PsiClassType exceptionType, String exceptionName, PsiElement context) throws IncorrectOperationException;

  XmlText createDisplayText(String s) throws IncorrectOperationException;

  XmlTag createXHTMLTagFromText(String s) throws IncorrectOperationException;

}
