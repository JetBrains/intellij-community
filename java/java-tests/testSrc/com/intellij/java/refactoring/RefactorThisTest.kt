// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.refactoring.actions.ConvertToInstanceMethodAction
import com.intellij.refactoring.actions.InheritanceToDelegationAction
import com.intellij.refactoring.actions.IntroduceFunctionalVariableAction
import com.intellij.refactoring.actions.IntroduceParameterObjectAction
import com.intellij.refactoring.actions.MakeStaticAction
import com.intellij.refactoring.actions.MethodDuplicatesAction
import com.intellij.refactoring.actions.MoveAction
import com.intellij.refactoring.actions.PullUpAction
import com.intellij.refactoring.actions.PushDownAction
import com.intellij.refactoring.actions.RefactoringQuickListPopupAction
import com.intellij.refactoring.actions.ReplaceMethodWithMethodObjectAction
import com.intellij.refactoring.actions.SafeDeleteAction
import com.intellij.refactoring.actions.TurnRefsToSuperAction
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class RefactorThisTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData")

  private val BASE_PATH: @NonNls String = "/refactoring/refactorThis"

  @Test
  fun testPullMembersUpWithExtends() {
    assertTrue(doActionExists<PullUpAction>())
  }

  @Test
  fun testPullMembersUpWithImplements() {
    assertTrue(doActionExists<PullUpAction>())
  }

  @Test
  fun testPullMembersUpFromAnonymousClass() {
    assertTrue(doActionExists<PullUpAction>())
  }

  @Test
  fun testPullMembersUpFiltered() {
    assertFalse(doActionExists<PullUpAction>())
  }

  @Test
  fun testInheritanceToDelegationWithExtends() {
    assertTrue(doActionExists<InheritanceToDelegationAction>())
  }

  @Test
  fun testInheritanceToDelegationWithImplements() {
    assertTrue(doActionExists<InheritanceToDelegationAction>())
  }

  @Test
  fun testInheritanceToDelegationNoSuperClass() {
    assertFalse(doActionExists<InheritanceToDelegationAction>())
  }

  @Test
  fun testInheritanceToDelegationOutsideDeclaration() {
    assertFalse(doActionExists<InheritanceToDelegationAction>())
  }

  @Test
  fun testReplaceMethodWithMethodObjectIsFiltered() {
    assertFalse(doActionExists<ReplaceMethodWithMethodObjectAction>())
  }

  @Test
  fun testFindAndReplaceDuplicatesIsFiltered() {
    assertFalse(doActionExists<MethodDuplicatesAction>())
  }

  @Test
  fun testFindAndReplaceDuplicatesOnMethodDeclaration() {
    assertTrue(doActionExists<MethodDuplicatesAction>())
  }

  @Test
  fun testFindAndReplaceDuplicatesOnFieldDeclaration() {
    assertTrue(doActionExists<MethodDuplicatesAction>())
  }

  @Test
  fun testUseInterfaceWherePossibleOnDeclaration() {
    assertTrue(doActionExists<TurnRefsToSuperAction>())
  }

  @Test
  fun testUseInterfaceWherePossibleFilteredOnReference() {
    assertFalse(doActionExists<TurnRefsToSuperAction>())
  }

  @Test
  fun testUseInterfaceWherePossibleIsFiltered() {
    assertFalse(doActionExists<TurnRefsToSuperAction>())
  }

  @Test
  fun testSafeDeleteIsFilteredOnClassReference() {
    assertFalse(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testSafeDeleteIsFilteredOnMethodReference() {
    assertFalse(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testSafeDeleteIsFilteredOnVariableReference() {
    assertFalse(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testSafeDeleteOnClassDeclaration() {
    assertTrue(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testSafeDeleteOnMethodDeclaration() {
    assertTrue(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testSafeDeleteOnVariableDeclaration() {
    assertTrue(doActionExists<SafeDeleteAction>())
  }

  @Test
  fun testMoveIsFilteredOnStatement() {
    assertFalse(doActionExists<MoveAction>())
  }

  @Test
  fun testMoveIsFilteredOnMethodReference() {
    assertFalse(doActionExists<MoveAction>())
  }

  @Test
  fun testMoveIsFilteredOnConstructor() {
    assertFalse(doActionExists<MoveAction>())
  }

  @Test
  fun testMoveOnMethodDeclaration() {
    assertTrue(doActionExists<MoveAction>())
  }

  @Test
  fun testMoveOnClassDeclaration() {
    assertTrue(doActionExists<MoveAction>())
  }

  @Test
  fun testIntroduceParameterObject() {
    assertTrue(doActionExists<IntroduceParameterObjectAction>())
  }

  @Test
  fun testIntroduceParameterObjectFiltered() {
    assertFalse(doActionExists<IntroduceParameterObjectAction>())
  }

  @Test
  fun testIntroduceParameterObjectFiltered2() {
    assertFalse(doActionExists<IntroduceParameterObjectAction>())
  }

  @Test
  fun testMakeStaticOnMethodDeclaration() {
    assertTrue(doActionExists<MakeStaticAction>())
  }

  @Test
  fun testMakeStaticOnClassDeclaration() {
    assertTrue(doActionExists<MakeStaticAction>())
  }

  @Test
  fun testMakeStaticFilteredOnStaticClass() {
    assertFalse(doActionExists<MakeStaticAction>())
  }

  @Test
  fun testMakeStaticFiltered() {
    assertFalse(doActionExists<MakeStaticAction>())
  }

  @Test
  fun testConvertToInstanceMethod() {
    assertTrue(doActionExists<ConvertToInstanceMethodAction>())
  }

  @Test
  fun testConvertToInstanceMethodFiltered() {
    assertFalse(doActionExists<ConvertToInstanceMethodAction>())
  }

  @Test
  fun testPushDownOnMethod() {
    assertTrue(doActionExists<PushDownAction>())
  }

  @Test
  fun testPushDownOnClass() {
    assertTrue(doActionExists<PushDownAction>())
  }

  @Test
  fun testPushDownFiltered() {
    assertFalse(doActionExists<PushDownAction>())
  }

  @Test
  fun testIntroduceFunctionalVariableFromExpression() {
    assertTrue(doActionExists<IntroduceFunctionalVariableAction>())
  }

  @Test
  fun testIntroduceFunctionalVariableFromStatement() {
    assertTrue(doActionExists<IntroduceFunctionalVariableAction>())
  }

  @Test
  fun testIntroduceFunctionalVariableFiltered() {
    assertFalse(doActionExists<IntroduceFunctionalVariableAction>())
  }

  private inline fun <reified A> doActionExists(): Boolean {
    myFixture.configureByFile("$BASE_PATH/${myFixture.getTestName(false)}.java")
    val actions = findAvailableActions()
    return actions.any { action -> action is A }
  }

  private fun findAvailableActions(): List<AnAction> {
    var actions = emptyList<AnAction>()
    myFixture.runInCommand {
      val action = RefactoringQuickListPopupAction()
      val group = DefaultActionGroup()
      val dataContext = (myFixture.editor as EditorEx).dataContext
      action.fillActions(myFixture.project, group, dataContext)
      actions = Utils.expandActionGroup(
        group, PresentationFactory(), dataContext,
        ActionPlaces.REFACTORING_QUICKLIST, ActionUiKind.POPUP)
    }
    return actions
  }

}