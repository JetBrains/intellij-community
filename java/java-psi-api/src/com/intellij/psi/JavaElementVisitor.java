// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.javadoc.*;

public abstract class JavaElementVisitor extends PsiElementVisitor {
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    visitClass(aClass);
  }

  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    visitExpression(expression);
  }

  public void visitAssertStatement(PsiAssertStatement statement) {
    visitStatement(statement);
  }

  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    visitPolyadicExpression(expression);
  }

  public void visitBlockStatement(PsiBlockStatement statement) {
    visitStatement(statement);
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    visitStatement(statement);
  }

  public void visitYieldStatement(PsiYieldStatement statement) {
    visitStatement(statement);
  }

  public void visitClass(PsiClass aClass) {
    visitElement(aClass);
  }

  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    visitElement(block);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    visitStatement(statement);
  }

  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    visitStatement(statement);
  }

  public void visitDocComment(PsiDocComment comment) {
    visitComment(comment);
  }

  public void visitDocTag(PsiDocTag tag) {
    visitElement(tag);
  }

  public void visitDocTagValue(PsiDocTagValue value) {
    visitElement(value);
  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitEmptyStatement(PsiEmptyStatement statement) {
    visitStatement(statement);
  }

  public void visitExpression(PsiExpression expression) {
    visitElement(expression);
  }

  public void visitExpressionList(PsiExpressionList list) {
    visitElement(list);
  }

  public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    visitStatement(statement);
  }

  public void visitExpressionStatement(PsiExpressionStatement statement) {
    visitStatement(statement);
  }

  public void visitField(PsiField field) {
    visitVariable(field);
  }

  public void visitForStatement(PsiForStatement statement) {
    visitStatement(statement);
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    visitStatement(statement);
  }

  public void visitIdentifier(PsiIdentifier identifier) {
    visitJavaToken(identifier);
  }

  public void visitIfStatement(PsiIfStatement statement) {
    visitStatement(statement);
  }

  public void visitImportList(PsiImportList list) {
    visitElement(list);
  }

  public void visitImportStatement(PsiImportStatement statement) {
    visitElement(statement);
  }

  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    visitElement(statement);
  }

  public void visitInlineDocTag(PsiInlineDocTag tag) {
    visitDocTag(tag);
  }

  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitJavaToken(PsiJavaToken token){
    visitElement(token);
  }

  public void visitKeyword(PsiKeyword keyword) {
    visitJavaToken(keyword);
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    visitStatement(statement);
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    visitExpression(expression);
  }

  public void visitLocalVariable(PsiLocalVariable variable) {
    visitVariable(variable);
  }

  public void visitMethod(PsiMethod method) {
    visitElement(method);
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    visitCallExpression(expression);
  }

  public void visitCallExpression(PsiCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitModifierList(PsiModifierList list) {
    visitElement(list);
  }

  public void visitNewExpression(PsiNewExpression expression) {
    visitCallExpression(expression);
  }

  public void visitPackage(PsiPackage aPackage) {
    visitElement(aPackage);
  }

  public void visitPackageStatement(PsiPackageStatement statement) {
    visitElement(statement);
  }

  public void visitParameter(PsiParameter parameter) {
    visitVariable(parameter);
  }

  public void visitRecordComponent(PsiRecordComponent recordComponent) {
    visitVariable(recordComponent);
  }

  public void visitReceiverParameter(PsiReceiverParameter parameter) {
    visitVariable(parameter);
  }

  public void visitParameterList(PsiParameterList list) {
    visitElement(list);
  }


  public void visitRecordHeader(PsiRecordHeader recordHeader) {
    visitElement(recordHeader);
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitUnaryExpression(PsiUnaryExpression expression) {
    visitExpression(expression);
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    visitElement(reference);
  }

  public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement reference) {
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
  public void visitReferenceExpression(PsiReferenceExpression expression) {}

  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    visitReferenceExpression(expression);
  }

  public void visitReferenceList(PsiReferenceList list) {
    visitElement(list);
  }

  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    visitElement(list);
  }

  public void visitTypeParameterList(PsiTypeParameterList list) {
    visitElement(list);
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    visitStatement(statement);
  }

  public void visitStatement(PsiStatement statement) {
    visitElement(statement);
  }

  public void visitSuperExpression(PsiSuperExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    visitStatement(statement);
  }

  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    visitStatement(statement);
  }

  public void visitThisExpression(PsiThisExpression expression) {
    visitExpression(expression);
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    visitStatement(statement);
  }

  public void visitTryStatement(PsiTryStatement statement) {
    visitStatement(statement);
  }

  public void visitCatchSection(PsiCatchSection section) {
    visitElement(section);
  }

  public void visitResourceList(PsiResourceList resourceList) {
    visitElement(resourceList);
  }

  public void visitResourceVariable(PsiResourceVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitResourceExpression(PsiResourceExpression expression) {
    visitElement(expression);
  }

  public void visitTypeElement(PsiTypeElement type) {
    visitElement(type);
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    visitExpression(expression);
  }

  public void visitVariable(PsiVariable variable) {
    visitElement(variable);
  }

  public void visitWhileStatement(PsiWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitJavaFile(PsiJavaFile file){
    visitFile(file);
  }

  public void visitImplicitVariable(ImplicitVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitDocToken(PsiDocToken token) {
    visitElement(token);
  }

  public void visitTypeParameter(PsiTypeParameter classParameter) {
    visitClass(classParameter);
  }

  public void visitAnnotation(PsiAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitAnnotationParameterList(PsiAnnotationParameterList list) {
    visitElement(list);
  }

  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    visitElement(initializer);
  }

  public void visitNameValuePair(PsiNameValuePair pair) {
    visitElement(pair);
  }

  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    visitMethod(method);
  }

  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    visitAnonymousClass(enumConstantInitializer);
  }

  public void visitCodeFragment(JavaCodeFragment codeFragment) {
    visitFile(codeFragment);
  }

  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    visitExpression(expression);
  }

  public void visitLambdaExpression(PsiLambdaExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchExpression(PsiSwitchExpression expression) {
    visitExpression(expression);
  }

  public void visitModule(PsiJavaModule module) {
    visitElement(module);
  }

  public void visitModuleReferenceElement(PsiJavaModuleReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitModuleStatement(PsiStatement statement) {
    visitStatement(statement);
  }

  public void visitRequiresStatement(PsiRequiresStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitPackageAccessibilityStatement(PsiPackageAccessibilityStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitUsesStatement(PsiUsesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitProvidesStatement(PsiProvidesStatement statement) {
    visitModuleStatement(statement);
  }

  public void visitPattern(PsiPattern pattern) {
    visitElement(pattern);
  }

  public void visitTypeTestPattern(PsiTypeTestPattern pattern) {
    visitPattern(pattern);
  }

  public void visitPatternVariable(PsiPatternVariable variable) {
    visitParameter(variable);
  }
}