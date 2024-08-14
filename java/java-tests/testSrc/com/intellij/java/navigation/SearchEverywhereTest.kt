// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.gotoByName.GotoActionTest
import com.intellij.idea.IJIgnore
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil.waitForFuture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.assertEquals
import com.intellij.util.Processor
import org.mockito.Mockito
import javax.swing.DefaultListCellRenderer
import javax.swing.ListCellRenderer

class SearchEverywhereTest : LightJavaCodeInsightFixtureTestCase() {
  companion object {
    const val SEARCH_TIMEOUT = 50000L
  }

  private var mySearchUI: SearchEverywhereUI? = null

  private lateinit var mixingResultsFlag: SEParam

  override fun setUp() {
    super.setUp()
    mixingResultsFlag = SEParam(
      { Experiments.getInstance().isFeatureEnabled("search.everywhere.mixed.results") },
      { Experiments.getInstance().setFeatureEnabled("search.everywhere.mixed.results", it) }
    )
  }

  override fun tearDown() {
    try {
      if (mySearchUI != null) {
        Disposer.dispose(mySearchUI!!)
        mySearchUI = null
      }
      mixingResultsFlag.reset()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test switch to external files when nothing is found`() {
    val strBuffer = myFixture.addClass("class StrBuffer{ }")
    val stringBuffer = myFixture.findClass("java.lang.StringBuffer")

    val ui = createTestUI(listOf(ChooseByNameTest.createClassContributor(project, testRootDisposable)))

    var future = ui.findElementsForPattern("StrBuffer")
    assertEquals(listOf(strBuffer), waitForFuture(future, SEARCH_TIMEOUT))

    future = ui.findElementsForPattern("StringBuffer")
    assertEquals(listOf(stringBuffer), waitForFuture(future, SEARCH_TIMEOUT))
  }

  fun `test mixing classes and files`() {
    mixingResultsFlag.set(true)

    val testClass = myFixture.addClass("class TestClass{}")
    val anotherTestClass = myFixture.addClass("class AnotherTestClass{}")
    val testFile = myFixture.addFileToProject("testClass.txt", "")

    val ui = createTestUI(listOf(
      ChooseByNameTest.createClassContributor(project, testRootDisposable),
      ChooseByNameTest.createFileContributor(project, testRootDisposable)
    ))

    var future = ui.findElementsForPattern("TestClass")
    assertEquals(listOf(testClass, testFile, anotherTestClass), waitForFuture(future, SEARCH_TIMEOUT))

    future = ui.findElementsForPattern("testClass")
    assertEquals(listOf(testFile, testClass, anotherTestClass), waitForFuture(future, SEARCH_TIMEOUT))

    val testClass2 = myFixture.addClass("class testClass2{}")

    future = ui.findElementsForPattern("testClass")
    assertEquals(listOf(testClass2, testFile, testClass, anotherTestClass), waitForFuture(future, SEARCH_TIMEOUT))
  }

  fun `test mixing results from stub contributors`() {
    mixingResultsFlag.set(true)

    val contributor1 = StubContributor("contributor1", 1)
    val contributor2 = StubContributor("contributor2", 2)
    val contributor3 = StubContributor("contributor3", 3)

    contributor1.addElements(mapOf("item1" to 10, "item2" to 12, "item3" to 8))
    contributor2.addElements(mapOf("item3" to 8, "item4" to 10, "item5" to 11, "item8" to 15))
    contributor3.addElements(mapOf("item3" to 8, "item5" to 11, "item6" to 10, "item7" to 15))

    val ui = createTestUI(listOf(contributor2, contributor1, contributor3))
    val future = ui.findElementsForPattern("ignored")
    assertEquals(listOf("item8", "item7", "item2", "item5", "item1", "item4", "item6", "item3"), waitForFuture(future, SEARCH_TIMEOUT))
  }

  @IJIgnore(issue = "IDEA-336674")
  fun `test priority for actions with space in pattern`() {
    mixingResultsFlag.set(true)

    val action1 = StubAction("Bravo Charlie")
    val action2 = StubAction("Alpha Bravo Charlie")
    val class1 = myFixture.addClass("class BravoCharlie{}")
    val class2 = myFixture.addClass("class AlphaBravoCharlie{}")

    val ui = createTestUI(listOf(
      ChooseByNameTest.createClassContributor(project, testRootDisposable),
      GotoActionTest.createActionContributor(project, testRootDisposable)
    ))

    val actions = mapOf("ia1" to action1, "ia2" to action2)
    val actionManager = ActionManager.getInstance()
    actions.forEach { (key, value) -> actionManager.registerAction(key, value) }
    try {
      var future = ui.findElementsForPattern("bravocharlie")
      var matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravocharlie")
      var matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravocharlie")
      assertEquals(listOf(class1, matchedAction1, class2, matchedAction2), waitForFuture(future, SEARCH_TIMEOUT))

      future = ui.findElementsForPattern("bravo charlie")
      matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravo charlie")
      matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravo charlie")
      assertEquals(listOf(matchedAction1, class1, matchedAction2, class2), waitForFuture(future, SEARCH_TIMEOUT))
    } finally {
      actions.forEach { (key, _) -> actionManager.unregisterAction(key) }
    }
  }

  @IJIgnore(issue = "IDEA-336671")
  fun `test top hit priority`() {
    mixingResultsFlag.set(true)

    val action1 = StubAction("Bravo Charlie")
    val action2 = StubAction("Alpha Bravo Charlie")
    val class1 = myFixture.addClass("class BravoCharlie{}")
    val class2 = myFixture.addClass("class AlphaBravoCharlie{}")

    val ui = createTestUI(listOf(
      ChooseByNameTest.createClassContributor(project, testRootDisposable),
      GotoActionTest.createActionContributor(project, testRootDisposable),
      TopHitSEContributor(project, null, null)
    ))

    val actions = mapOf("ia1" to action1, "ia2" to action2)
    val actionManager = ActionManager.getInstance()
    val abbreviationManager = AbbreviationManager.getInstance()
    actions.forEach { (key, value) -> actionManager.registerAction(key, value) }
    try {
      val matchedAction1 = GotoActionTest.createMatchedAction(action1, "bravo")
      val matchedAction2 = GotoActionTest.createMatchedAction(action2, "bravo")

      // filter out occasional matches in IDE actions
      val testElements = setOf(action1, action2, matchedAction1, matchedAction2, class1, class2)

      var future = ui.findElementsForPattern("bravo")
      var bravoResult = waitForFuture(future, SEARCH_TIMEOUT)
      assert(bravoResult.filter { it in testElements } == listOf(class1, matchedAction1, class2, matchedAction2))

      abbreviationManager.register("bravo", "ia2")
      future = ui.findElementsForPattern("bravo")
      bravoResult = waitForFuture(future, SEARCH_TIMEOUT)
      assertEquals(listOf(action2, class1, matchedAction1, class2), bravoResult.filter { it in testElements })
    }
    finally {
      actions.forEach { (key, _) -> actionManager.unregisterAction(key) }
      abbreviationManager.removeAllAbbreviations("ia2")
    }
  }

  fun `test abbreviations on top`() {
    val abbreviationManager = AbbreviationManager.getInstance()
    val actionManager = ActionManager.getInstance()
    val ui = createTestUI(listOf(GotoActionTest.createActionContributor(project, testRootDisposable)))

    try {
      abbreviationManager.register("cp", "CloseProject")
      val future = ui.findElementsForPattern("cp")
      val firstItem = waitForFuture(future, SEARCH_TIMEOUT).firstOrNull()
      val matchedAction = GotoActionTest.createMatchedAction(actionManager.getAction("CloseProject"), "cp")
      assertEquals(matchedAction, firstItem)
    }
    finally {
      abbreviationManager.remove("cp", "CloseProject")
    }

    try {
      abbreviationManager.register("cp", "ScanSourceCommentsAction")
      val future = ui.findElementsForPattern("cp")
      val firstItem = waitForFuture(future, SEARCH_TIMEOUT)[0]
      val matchedAction = GotoActionTest.createMatchedAction(actionManager.getAction("ScanSourceCommentsAction"), "cp")
      assertEquals(matchedAction, firstItem)
    } finally {
      abbreviationManager.remove("cp", "ScanSourceCommentsAction")
    }
  }

  fun `test recent files at the top of results`() {
    mixingResultsFlag.set(true)

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

      val recentFilesContributor = RecentFilesSEContributor(ChooseByNameTest.createEvent(project))
      Disposer.register(testRootDisposable, recentFilesContributor)
      val ui = createTestUI(listOf(
        ChooseByNameTest.createFileContributor(project, testRootDisposable),
        recentFilesContributor
      ))

      var future = ui.findElementsForPattern("appfile")
      assertEquals(listOf(file2, file1, file4, file3, file6, file5), waitForFuture(future, SEARCH_TIMEOUT))

      myFixture.openFileInEditor(file4.originalFile.virtualFile)
      myFixture.openFileInEditor(file3.originalFile.virtualFile)
      myFixture.openFileInEditor(file5.originalFile.virtualFile)
      myFixture.openFileInEditor(wrongFile.originalFile.virtualFile)
      future = ui.findElementsForPattern("appfile")
      assertEquals(listOf(file4, file3, file5, file2, file1, file6), waitForFuture(future, SEARCH_TIMEOUT))
    } finally {
      AdvancedSettings.setBoolean("search.everywhere.recent.at.top", savedFlag)
    }
  }

  fun `test search events topic`() {
    val contributor1 = StubContributor("contributor1", 1)
    val contributor2 = StubContributor("contributor2", 2)
    contributor1.addElements(mapOf("item1" to 10, "item2" to 12, "item3" to 8))
    contributor2.addElements(mapOf("item3" to 9, "item4" to 10, "item5" to 11))

    val ui = createTestUI(listOf(contributor1, contributor2))
    val mockListener: SearchListener = Mockito.mock(SearchListener::class.java)
    ApplicationManager.getApplication().messageBus.connect(ui).subscribe(SearchEverywhereUI.SEARCH_EVENTS, mockListener)
    val future = ui.findElementsForPattern("ignored")
    waitForFuture(future, SEARCH_TIMEOUT)

    val inOrder = Mockito.inOrder(mockListener)
    inOrder.verify(mockListener).searchStarted(Mockito.eq("ignored"), Mockito.any())
    inOrder.verify(mockListener, Mockito.atLeastOnce()).elementsAdded(Mockito.any())
    inOrder.verify(mockListener, Mockito.times(2)).contributorFinished(Mockito.any(), Mockito.eq(false))
    inOrder.verify(mockListener).searchFinished(Mockito.any())
  }

  private fun createTestUI(contributorsList: List<SearchEverywhereContributor<out Any>>): SearchEverywhereUI {
    mySearchUI?.let { Disposer.dispose(it) }

    mySearchUI = SearchEverywhereUI(project, contributorsList)
    val tab = if (contributorsList.size > 1)
      SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
    else
      contributorsList.first().getSearchProviderId()
    mySearchUI?.switchToTab(tab)
    return mySearchUI!!
  }

  private class StubContributor(private val myId: String, private val mySortWeight: Int) :
    WeightedSearchEverywhereContributor<Any> {

    private val myElements = mutableMapOf<Any, Int>()

    fun addElements(elements: Map<Any, Int>) {
      myElements.putAll(elements)
    }

    override fun fetchWeightedElements(
      pattern: String,
      progressIndicator: ProgressIndicator,
      consumer: Processor<in FoundItemDescriptor<Any>>
    ) {
      myElements.forEach { (element, weight) ->
        consumer.process(FoundItemDescriptor(element, weight))
      }
    }

    override fun getSearchProviderId(): String = myId

    override fun getGroupName(): String = myId

    override fun getSortWeight(): Int = mySortWeight

    override fun showInFindResults(): Boolean = false

    override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean = false

    override fun getElementsRenderer(): ListCellRenderer<in Any> = DefaultListCellRenderer()
  }

  private class StubAction(text: String) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {}
  }

  private class SEParam(private val getter: () -> Boolean, private val setter: (Boolean) -> Unit) {
    private val defaultValue: Boolean = getter()

    fun get(): Boolean = getter()

    fun set(value: Boolean) {
      if (get() == value) return
      setter(value)
    }

    fun reset() {
      set(defaultValue)
    }
  }
}