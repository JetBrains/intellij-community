// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.javadoc.*;
import org.jetbrains.annotations.NotNull;

public abstract class JavaElementVisitor extends PsiElementVisitor {
  public void visitAnnotation(@NotNull PsiAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitAnnotationArrayInitializer(@NotNull PsiArrayInitializerMemberValue initializer) {
    visitElement(initializer);
  }

  public void visitAnnotationMethod(@NotNull PsiAnnotationMethod method) {
    visitMethod(method);
  }

  public void visitAnnotationParameterList(@NotNull PsiAnnotationParameterList list) {
    visitElement(list);
  }

  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    visitClass(aClass);
  }

  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    visitExpression(expression);
  }

  public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
    visitStatement(statement);
  }

  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
    visitPolyadicExpression(expression);
  }

  public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
    visitStatement(statement);
  }

  public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
    visitStatement(statement);
  }

  public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitCaseLabelElementList(@NotNull PsiCaseLabelElementList list) {
    visitElement(list);
  }

  public void visitCatchSection(@NotNull PsiCatchSection section) {
    visitElement(section);
  }

  public void visitClass(@NotNull PsiClass aClass) {
    visitElement(aClass);
  }

  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitCodeBlock(@NotNull PsiCodeBlock block) {
    visitElement(block);
  }

  public void visitCodeFragment(@NotNull JavaCodeFragment codeFragment) {
    visitFile(codeFragment);
  }

  public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
    visitStatement(statement);
  }

  public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
    visitStatement(statement);
  }

  public void visitDeconstructionList(@NotNull PsiDeconstructionList deconstructionList) { visitElement(deconstructionList); }

  public void visitDeconstructionPattern(@NotNull PsiDeconstructionPattern deconstructionPattern) { visitPattern(deconstructionPattern); }

  public void visitDefaultCaseLabelElement(@NotNull PsiDefaultCaseLabelElement element) {
    visitElement(element);
  }

  public void visitDocComment(@NotNull PsiDocComment comment) {
    visitComment(comment);
  }

  public void visitDocTag(@NotNull PsiDocTag tag) {
    visitElement(tag);
  }

  public void visitDocTagValue(@NotNull PsiDocTagValue value) {
    visitElement(value);
  }

  public void visitDocToken(@NotNull PsiDocToken token) {
    visitElement(token);
  }

  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitEmptyStatement(@NotNull PsiEmptyStatement statement) {
    visitStatement(statement);
  }

  public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitEnumConstantInitializer(@NotNull PsiEnumConstantInitializer enumConstantInitializer) {
    visitAnonymousClass(enumConstantInitializer);
  }

  public void visitExpression(@NotNull PsiExpression expression) {
    visitElement(expression);
  }

  public void visitExpressionList(@NotNull PsiExpressionList list) {
    visitElement(list);
  }

  public void visitExpressionListStatement(@NotNull PsiExpressionListStatement statement) {
    visitStatement(statement);
  }

  public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
    visitStatement(statement);
  }

  public void visitField(@NotNull PsiField field) {
    visitVariable(field);
  }

  public void visitForeachPatternStatement(@NotNull PsiForeachPatternStatement statement) {
    visitForeachStatementBase(statement);
  }

  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    visitForeachStatementBase(statement);
  }

  public void visitForeachStatementBase(@NotNull PsiForeachStatementBase statement) {
    visitStatement(statement);
  }

  public void visitForStatement(@NotNull PsiForStatement statement) {
    visitStatement(statement);
  }

  public void visitFragment(@NotNull PsiFragment fragment) {
    visitElement(fragment);
  }

  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    visitJavaToken(identifier);
  }

  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    visitStatement(statement);
  }

  public void visitImplicitClass(@NotNull PsiImplicitClass aClass) {
    visitClass(aClass);
  }

  public void visitImplicitVariable(@NotNull ImplicitVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitImportList(@NotNull PsiImportList list) {
    visitElement(list);
  }

  public void visitImportModuleStatement(@NotNull PsiImportModuleStatement statement) {
    visitElement(statement);
  }

  public void visitImportStatement(@NotNull PsiImportStatement statement) {
    visitElement(statement);
  }

  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement reference) {
    visitReferenceElement(reference);
  }

  public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
    visitElement(statement);
  }

  public void visitInlineDocTag(@NotNull PsiInlineDocTag tag) {
    visitDocTag(tag);
  }

  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitJavaFile(@NotNull PsiJavaFile file){
    visitFile(file);
  }

  public void visitJavaToken(@NotNull PsiJavaToken token){
    visitElement(token);
  }

  public void visitKeyword(@NotNull PsiKeyword keyword) {
    visitJavaToken(keyword);
  }

  public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
    visitStatement(statement);
  }

  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
     visitExpression(expression);
   }

  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    visitExpression(expression);
  }

  public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
    visitVariable(variable);
  }

  public void visitMarkdownCodeBlock(@NotNull PsiMarkdownCodeBlock block) {
    visitElement(block);
  }

  public void visitMarkdownReferenceLink(@NotNull PsiMarkdownReferenceLink referenceLink) {
    visitElement(referenceLink);
  }

  public void visitMethod(@NotNull PsiMethod method) {
    visitElement(method);
  }

  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    visitCallExpression(expression);
  }

  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    visitReferenceExpression(expression);
  }

  public void visitModifierList(@NotNull PsiModifierList list) {
    visitElement(list);
  }

  public void visitModule(@NotNull PsiJavaModule module) {
    visitElement(module);
  }

  public void visitModuleReferenceElement(@NotNull PsiJavaModuleReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitModuleStatement(@NotNull PsiStatement statement) {
    visitStatement(statement);
  }

  public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
    visitElement(pair);
  }

  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    visitCallExpression(expression);
  }

  public void visitPackage(@NotNull PsiPackage aPackage) {
    visitElement(aPackage);
  }

  public void visitPackageAccessibilityStatement(@NotNull PsiPackageAccessibilityStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitPackageStatement(@NotNull PsiPackageStatement statement) {
    visitElement(statement);
  }

  public void visitParameter(@NotNull PsiParameter parameter) {
    visitVariable(parameter);
  }

  public void visitParameterList(@NotNull PsiParameterList list) {
    visitElement(list);
  }

  public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitPattern(@NotNull PsiPattern pattern) {
    visitElement(pattern);
  }

  public void visitPatternVariable(@NotNull PsiPatternVariable variable) {
    visitParameter(variable);
  }

  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
     visitExpression(expression);
   }

  public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitProvidesStatement(@NotNull PsiProvidesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitReceiverParameter(@NotNull PsiReceiverParameter parameter) {
    visitVariable(parameter);
  }

  public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
    visitVariable(recordComponent);
  }

  public void visitRecordHeader(@NotNull PsiRecordHeader recordHeader) {
    visitElement(recordHeader);
  }

  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    visitElement(reference);
  }

  /**
   * PsiReferenceExpression is PsiReferenceElement and PsiExpression at the same time.
   * If we'd call both visitReferenceElement and visitExpression in default implementation
   * of this method we can easily stuck with exponential algorithm if the derived visitor
   * extends visitElement() and accepts children there.
   * {@link JavaRecursiveElementVisitor} knows that and implements this method accordingly.
   * All other visitor must decide themselves what implementation (visitReferenceElement() or visitExpression() or none or LOG.error())
   * is appropriate for them.
   */
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {}

  public void visitReferenceList(@NotNull PsiReferenceList list) {
    visitElement(list);
  }

  public void visitReferenceParameterList(@NotNull PsiReferenceParameterList list) {
    visitElement(list);
  }

  public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitResourceExpression(@NotNull PsiResourceExpression expression) {
    visitElement(expression);
  }

  public void visitResourceList(@NotNull PsiResourceList resourceList) {
    visitElement(resourceList);
  }

  public void visitResourceVariable(@NotNull PsiResourceVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    visitStatement(statement);
  }

  public void visitSnippetAttribute(@NotNull PsiSnippetAttribute attribute) {
    visitElement(attribute);
  }

  public void visitSnippetAttributeList(@NotNull PsiSnippetAttributeList attributeList) {
    visitElement(attributeList);
  }

  public void visitSnippetAttributeValue(@NotNull PsiSnippetAttributeValue attributeValue) {
    visitElement(attributeValue);
  }

  public void visitSnippetDocTagBody(@NotNull PsiSnippetDocTagBody body) {
    visitElement(body);
  }

  public void visitSnippetDocTagValue(@NotNull PsiSnippetDocTagValue value) {
    visitElement(value);
  }

  public void visitSnippetTag(@NotNull PsiSnippetDocTag snippetDocTag) {
    visitInlineDocTag(snippetDocTag);
  }
  
  public void visitStatement(@NotNull PsiStatement statement) {
    visitElement(statement);
  }

  public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    visitStatement(statement);
  }

  public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
    visitStatement(statement);
  }

  public void visitTemplate(@NotNull PsiTemplate template) {
    visitElement(template);
  }

  public void visitTemplateExpression(@NotNull PsiTemplateExpression expression) {
    visitExpression(expression);
  }

  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    visitExpression(expression);
  }

  public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
    visitStatement(statement);
  }

  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    visitStatement(statement);
  }

  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
    visitExpression(expression);
  }

  public void visitTypeElement(@NotNull PsiTypeElement type) {
    visitElement(type);
  }

  public void visitTypeParameter(@NotNull PsiTypeParameter classParameter) {
    visitClass(classParameter);
  }

  public void visitTypeParameterList(@NotNull PsiTypeParameterList list) {
    visitElement(list);
  }

  public void visitTypeTestPattern(@NotNull PsiTypeTestPattern pattern) {
    visitPattern(pattern);
  }

  public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
    visitExpression(expression);
  }

  public void visitUnnamedPattern(@NotNull PsiUnnamedPattern pattern) {
    visitPattern(pattern);
  }

  public void visitUsesStatement(@NotNull PsiUsesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitVariable(@NotNull PsiVariable variable) {
    visitElement(variable);
  }

  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
    visitStatement(statement);
  }
}