// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.SplitSearchListener
import com.intellij.java.ide.gotoByName.GotoActionTest
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.tabs.classes.SeClassesTab
import com.intellij.platform.searchEverywhere.testFramework.SePopupTestFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class SearchEverywhereTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test switch to external files when nothing is found`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val strBuffer = myFixture.addClass("class StrBuffer{ }")
    val stringBuffer = readAction { myFixture.findClass("java.lang.StringBuffer") }
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeClassesTab.ID)

      assertEquals(listOf(strBuffer), popup.findElements("StrBuffer"))
      assertFalse(popup.isEverywhere)

      // The project has no StringBuffer, so the tab turns on the everywhere filter and searches again.
      assertThat(popup.findElements("StringBuffer")).isNullOrEmpty()
      assertTrue(popup.isEverywhere)
      assertEquals(listOf(stringBuffer), popup.findElements("StringBuffer"))
    }
  }

  @Test
  fun `test mixing classes and files`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val testClass = myFixture.addClass("class TestClass{}")
    val anotherTestClass = myFixture.addClass("class AnotherTestClass{}")
    val testFile = myFixture.addFileToProject("testClass.txt", "")
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeAllTab.ID)

      assertEquals(listOf(testClass, testFile, anotherTestClass), popup.findPsiElements("TestClass"))
      assertEquals(listOf(testFile, testClass, anotherTestClass), popup.findPsiElements("testClass"))

      val testClass2 = myFixture.addClass("class testClass2{}")
      assertEquals(listOf(testClass2, testFile, testClass, anotherTestClass), popup.findPsiElements("testClass"))
    }
  }

  @Test
  fun `test priority for actions with space in pattern`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val action1 = StubAction("Bravo Charlie")
    val action2 = StubAction("Alpha Bravo Charlie")
    val class1 = myFixture.addClass("class BravoCharlie{}")
    val class2 = myFixture.addClass("class AlphaBravoCharlie{}")
    registerActions(mapOf("ia1" to action1, "ia2" to action2))
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeAllTab.ID)

      var matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravocharlie")
      var matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravocharlie")
      assertEquals(listOf(class1, matchedAction1, class2, matchedAction2),
                   popup.findElements("bravocharlie", among = setOf(class1, class2, matchedAction1, matchedAction2)))

      matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravo charlie")
      matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravo charlie")
      assertEquals(listOf(matchedAction1, class1, matchedAction2, class2),
                   popup.findElements("bravo charlie", among = setOf(class1, class2, matchedAction1, matchedAction2)))
    }
  }

  @Test
  fun `test top hit priority`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val action1 = StubAction("Bravo Charlie")
    val action2 = StubAction("Alpha Bravo Charlie")
    val class1 = myFixture.addClass("class BravoCharlie{}")
    val class2 = myFixture.addClass("class AlphaBravoCharlie{}")
    registerActions(mapOf("ia1" to action1, "ia2" to action2))
    val matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravo")
    val matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravo")
    val testElements = setOf(action1, action2, matchedAction1, matchedAction2, class1, class2)
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeAllTab.ID)

      assertEquals(listOf(class1, matchedAction1, class2, matchedAction2), popup.findElements("bravo", among = testElements))

      registerAbbreviation("bravo", "ia2")
      assertEquals(listOf(action2, class1, matchedAction1, class2), popup.findElements("bravo", among = testElements))
    }
  }

  @Test
  fun `test abbreviations on top`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val actionManager = ActionManager.getInstance()
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeActionsTab.ID)

      registerAbbreviation("cp", "CloseProject")
      assertEquals(GotoActionTest.createMatchedAction(actionManager.getAction("CloseProject"), "cp"), popup.findElements("cp").firstOrNull())
      AbbreviationManager.getInstance().remove("cp", "CloseProject")

      registerAbbreviation("cp", "SaveAll")
      assertEquals(GotoActionTest.createMatchedAction(actionManager.getAction("SaveAll"), "cp"), popup.findElements("cp").firstOrNull())
    }
  }

  @Test
  fun `test recent files at the top of results`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val savedFlag = AdvancedSettings.getBoolean("search.everywhere.recent.at.top")
    AdvancedSettings.setBoolean("search.everywhere.recent.at.top", true)
    try {
      val file1 = myFixture.addFileToProject("ApplicationFile.txt", "")
      val file2 = myFixture.addFileToProject("AppFile.txt", "")
      val file3 = myFixture.addFileToProject("ActionPerformerPreviewFile.txt", "")
      val file4 = myFixture.addFileToProject("AppInfoFile.txt", "")
      val file5 = myFixture.addFileToProject("SecondAppInfoFile.txt", "")
      val file6 = myFixture.addFileToProject("SecondAppFile.txt", "")
      val wrongFile = myFixture.addFileToProject("wrong.txt", "")
      withContext(Dispatchers.EDT) {
        val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeAllTab.ID)

        assertEquals(listOf(file2, file1, file4, file3, file6, file5), popup.findPsiElements("appfile"))

        myFixture.openFileInEditor(file4.originalFile.virtualFile)
        myFixture.openFileInEditor(file3.originalFile.virtualFile)
        myFixture.openFileInEditor(file5.originalFile.virtualFile)
        myFixture.openFileInEditor(wrongFile.originalFile.virtualFile)
        assertEquals(listOf(file4, file3, file5, file2, file1, file6), popup.findPsiElements("appfile"))
      }
    }
    finally {
      AdvancedSettings.setBoolean("search.everywhere.recent.at.top", savedFlag)
    }
  }

  @Test
  fun `test search events`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.addClass("class TestClass{}")
    withContext(Dispatchers.EDT) {
      val popup = SePopupTestFixture.open(myFixture.project, myFixture.testRootDisposable, SeClassesTab.ID)
      val events = mutableListOf<String>()
      popup.addSearchListener(object : SplitSearchListener {
        override fun searchStarted(pattern: String, tabId: String) {
          events += "started '$pattern' in $tabId"
        }

        override fun elementsAdded(uuidToElement: Map<String, Any>) {
          events += "added ${uuidToElement.size}"
        }

        override fun searchFinished(count: Int) {
          events += "finished $count"
        }
      })

      popup.findElements("TestClass")

      // The search of the empty pattern can send its last events after the listener is added.
      // The popup can also run the search again, for example when the dumb mode changes. So the last search counts.
      val startedEvent = "started 'TestClass' in ${SeClassesTab.ID}"
      val eventsOfSearch = events.drop(events.lastIndexOf(startedEvent))
      assertEquals(listOf(startedEvent, "added 1", "finished 1"), eventsOfSearch)
    }
  }

  private fun registerActions(actions: Map<String, AnAction>) {
    val actionManager = ActionManager.getInstance()
    actions.forEach { actionManager.registerAction(it.key, it.value) }
    Disposer.register(myFixture.testRootDisposable) { actions.keys.forEach { actionManager.unregisterAction(it) } }
  }

  private fun registerAbbreviation(abbreviation: String, actionId: String) {
    AbbreviationManager.getInstance().register(abbreviation, actionId)
    Disposer.register(myFixture.testRootDisposable) { AbbreviationManager.getInstance().remove(abbreviation, actionId) }
  }

  /** Searches for [pattern] and returns only the PSI elements. The All tab also shows the text search results. */
  private fun SePopupTestFixture.findPsiElements(pattern: String): List<PsiElement> = findElements(pattern).filterIsInstance<PsiElement>()

  /** Searches for [pattern] and returns only the elements in [among]. A tab also shows the matches in the IDE itself. */
  private fun SePopupTestFixture.findElements(pattern: String, among: Set<Any>): List<Any> = findElements(pattern).filter { it in among }

  private class StubAction(text: String) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {}
  }
}
