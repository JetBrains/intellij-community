// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.gotoByName.GotoActionTest
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.Consumer
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull

import javax.swing.*
import java.util.function.Supplier

import static com.intellij.testFramework.PlatformTestUtil.waitForFuture

class SearchEverywhereTest extends LightJavaCodeInsightFixtureTestCase {
  static final int SEARCH_TIMEOUT = 50_000

  SearchEverywhereUI mySearchUI

  private SEParam mixingResultsFlag
  private SEParam twoTabsFlag

  void setUp() {
    super.setUp()

    mixingResultsFlag = new SEParam(
            { Experiments.getInstance().isFeatureEnabled("search.everywhere.mixed.results")},
            { Boolean it -> Experiments.getInstance().setFeatureEnabled("search.everywhere.mixed.results", it)}
    )
    twoTabsFlag = new SEParam(
            { Registry.is("search.everywhere.group.contributors.by.type") },
            { Boolean it -> Registry.get("search.everywhere.group.contributors.by.type").setValue(it) }
    )
  }

  @Override
  protected void tearDown() throws Exception {
    if (mySearchUI != null) {
      Disposer.dispose(mySearchUI)
      mySearchUI = null
    }

    mixingResultsFlag.reset()
    twoTabsFlag.reset()

    super.tearDown()
  }

  void "test switch to external files when nothing is found"() {
    twoTabsFlag.set(false)

    def strBuffer = myFixture.addClass("class StrBuffer{ }")
    def stringBuffer = myFixture.findClass("java.lang.StringBuffer")

    def ui = createTestUI([ChooseByNameTest.createClassContributor(project, testRootDisposable)])

    def future = ui.findElementsForPattern("StrBuffer")
    assert waitForFuture(future, SEARCH_TIMEOUT) == [strBuffer]

    future = ui.findElementsForPattern("StringBuffer")
    assert waitForFuture(future, SEARCH_TIMEOUT) == [stringBuffer]
  }

  void "test mixing classes and files"() {
    mixingResultsFlag.set(true)
    twoTabsFlag.set(false)

    def testClass = myFixture.addClass("class TestClass{}")
    def anotherTestClass = myFixture.addClass("class AnotherTestClass{}")
    def testFile = myFixture.addFileToProject("testClass.txt", "")

    def ui = createTestUI([
      ChooseByNameTest.createClassContributor(project, testRootDisposable),
      ChooseByNameTest.createFileContributor(project, testRootDisposable)
    ])

    def future = ui.findElementsForPattern("TestClass")
    assert waitForFuture(future, SEARCH_TIMEOUT) == [testClass, testFile, anotherTestClass]

    future = ui.findElementsForPattern("testClass")
    assert waitForFuture(future, SEARCH_TIMEOUT) == [testFile, testClass, anotherTestClass]

    def testClass2 = myFixture.addClass("class testClass2{}")

    future = ui.findElementsForPattern("testClass")
    assert waitForFuture(future, SEARCH_TIMEOUT) == [testClass2, testFile, testClass, anotherTestClass]
  }

  void "test mixing results from stub contributors"() {
    mixingResultsFlag.set(true)
    twoTabsFlag.set(false)

    def contributor1 = new StubContributor("contributor1", 1)
    def contributor2 = new StubContributor("contributor2", 2)
    def contributor3 = new StubContributor("contributor3", 3)

    contributor1.addElements(["item1": 10, "item2": 12, "item3": 8])
    contributor2.addElements(["item3": 8, "item4": 10, "item5": 11, "item8": 15])
    contributor3.addElements(["item3": 8, "item5": 11, "item6": 10, "item7": 15])

    def ui = createTestUI([contributor2, contributor1, contributor3])
    def future = ui.findElementsForPattern("ignored")
    assert waitForFuture(future, SEARCH_TIMEOUT) == ["item8", "item7", "item2", "item5", "item1", "item4", "item6", "item3"]
  }

  void "test priority for actions with space in pattern"() {
    mixingResultsFlag.set(true)
    twoTabsFlag.set(false)

    def action1 = new StubAction("Imaginary Action")
    def action2 = new StubAction("Another Imaginary Action")
    def class1 = myFixture.addClass("class ImaginaryAction{}")
    def class2 = myFixture.addClass("class AnotherImaginaryAction{}")

    def ui = createTestUI([
            ChooseByNameTest.createClassContributor(project, testRootDisposable),
            GotoActionTest.createActionContributor(project, testRootDisposable)
    ])

    def actions = ["ia1": action1, "ia2": action2]
    def actionManager = ActionManager.getInstance()
    actions.each {actionManager.registerAction(it.key, it.value)}
    try {
      def future = ui.findElementsForPattern("imaginaryaction")
      def matchedAction1 = GotoActionTest.createMatchedAction(project, action1, "imaginaryaction")
      def matchedAction2 = GotoActionTest.createMatchedAction(project, action2, "imaginaryaction")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [class1, matchedAction1, class2, matchedAction2]

      future = ui.findElementsForPattern("imaginary action")
      matchedAction1 = GotoActionTest.createMatchedAction(project, action1, "imaginary action")
      matchedAction2 = GotoActionTest.createMatchedAction(project, action2, "imaginary action")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [matchedAction1, class1, matchedAction2,  class2]
    }
    finally {
      actions.each {actionManager.unregisterAction(it.key)}
    }
  }

  void "test top hit priority"() {
    mixingResultsFlag.set(true)
    twoTabsFlag.set(false)

    def action1 = new StubAction("Imaginary Action")
    def action2 = new StubAction("Another Imaginary Action")
    def class1 = myFixture.addClass("class ImaginaryAction{}")
    def class2 = myFixture.addClass("class AnotherImaginaryAction{}")

    def ui = createTestUI([
            ChooseByNameTest.createClassContributor(project, testRootDisposable),
            GotoActionTest.createActionContributor(project, testRootDisposable),
            new TopHitSEContributor(project, null, null)
    ])

    def actions = ["ia1": action1, "ia2": action2]
    def actionManager = ActionManager.getInstance()
    def abbreviationManager = AbbreviationManager.getInstance()
    actions.each {actionManager.registerAction(it.key, it.value)}
    try {
      def matchedAction1 = GotoActionTest.createMatchedAction(project, action1, "imaginary")
      def matchedAction2 = GotoActionTest.createMatchedAction(project, action2, "imaginary")
      def future = ui.findElementsForPattern("imaginary")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [class1, matchedAction1, class2, matchedAction2]

      abbreviationManager.register("imaginary", "ia2")
      future = ui.findElementsForPattern("imaginary")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [action2, class1, matchedAction1, class2]
    }
    finally {
      actions.each {actionManager.unregisterAction(it.key)}
      abbreviationManager.removeAllAbbreviations("ia2" )
    }
  }

  void "test abbreviations on top"() {
    twoTabsFlag.set(false)

    def abbreviationManager = AbbreviationManager.getInstance()
    def actionManager = ActionManager.getInstance()
    def ui = createTestUI([GotoActionTest.createActionContributor(project, testRootDisposable)])

    try {
      abbreviationManager.register("cp", "CloseProject")
      def future = ui.findElementsForPattern("cp")
      def firstItem = waitForFuture(future, SEARCH_TIMEOUT)[0]
      def matchedAction = GotoActionTest.createMatchedAction(project, actionManager.getAction("CloseProject"), "cp")
      assert firstItem == matchedAction
    }
    finally {
      abbreviationManager.remove("cp", "CloseProject")
    }

    try {
      abbreviationManager.register("cp", "ScanSourceCommentsAction")
      def future = ui.findElementsForPattern("cp")
      def firstItem = waitForFuture(future, SEARCH_TIMEOUT)[0]
      def matchedAction = GotoActionTest.createMatchedAction(project, actionManager.getAction("ScanSourceCommentsAction"), "cp")
      assert matchedAction == firstItem
    }
    finally {
      abbreviationManager.remove("cp", "ScanSourceCommentsAction")
    }
  }

  void "test recent files at the top of results"() {
    mixingResultsFlag.set(true)
    twoTabsFlag.set(false)

    def registryValue = Registry.get("search.everywhere.recent.at.top")
    def savedFlag = registryValue.asBoolean()
    registryValue.setValue(true)
    try {
      def file1 = myFixture.addFileToProject("ApplicationFile.txt", "")
      def file2 = myFixture.addFileToProject("AppFile.txt", "")
      def file3 = myFixture.addFileToProject("ActionPerformerPreviewFile.txt", "")
      def file4 = myFixture.addFileToProject("AppInfoFile.txt", "")
      def file5 = myFixture.addFileToProject("SecondAppInfoFile.txt", "")
      def file6 = myFixture.addFileToProject("SecondAppFile.txt", "")
      def wrongFile = myFixture.addFileToProject("wrong.txt", "")

      def recentFilesContributor = new RecentFilesSEContributor(ChooseByNameTest.createEvent(project))
      Disposer.register(testRootDisposable, recentFilesContributor)
      def ui = createTestUI([
        ChooseByNameTest.createFileContributor(project, testRootDisposable),
        recentFilesContributor
      ])

      def future = ui.findElementsForPattern("appfile")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [file2, file1, file4, file3, file6, file5]

      myFixture.openFileInEditor(file4.getOriginalFile().getVirtualFile())
      myFixture.openFileInEditor(file3.getOriginalFile().getVirtualFile())
      myFixture.openFileInEditor(file5.getOriginalFile().getVirtualFile())
      myFixture.openFileInEditor(wrongFile.getOriginalFile().getVirtualFile())
      future = ui.findElementsForPattern("appfile")
      assert waitForFuture(future, SEARCH_TIMEOUT) == [file4, file3, file5, file2, file1, file6]
    }
    finally {
      registryValue.setValue(savedFlag)
    }
  }

  private SearchEverywhereUI createTestUI(List<SearchEverywhereContributor<Object>> contributors) {
    def map = new HashMap<SearchEverywhereContributor<?>, SearchEverywhereTabDescriptor>()
    contributors.forEach({map.put(it, null)})
    return createTestUI(map)
  }

  private SearchEverywhereUI createTestUI(Map<SearchEverywhereContributor<?>, SearchEverywhereTabDescriptor> contributorsMap) {
    if (mySearchUI != null) Disposer.dispose(mySearchUI)

    mySearchUI = new SearchEverywhereUI(project, contributorsMap)
    def tab = contributorsMap.size() > 1
      ? SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
      : contributorsMap.keySet().find().getSearchProviderId()
    mySearchUI.switchToTab(tab)
    return mySearchUI
  }

  private static class StubContributor implements WeightedSearchEverywhereContributor<Object> {

    private final String myId
    private final int mySortWeight
    private final Map<Object, Integer> myElements = new HashMap<>()

    StubContributor(String id, int sortWeight) {
      myId = id
      mySortWeight = sortWeight
    }

    void addElements(Map<Object, Integer> elements) {
      myElements.putAll(elements)
    }

    @Override
    void fetchWeightedElements(@NotNull String pattern,
                               @NotNull ProgressIndicator progressIndicator,
                               @NotNull Processor<? super FoundItemDescriptor<Object>> consumer) {
      myElements.forEach({element, weight -> consumer.process(new FoundItemDescriptor(element, weight))})
    }

    @Override
    String getSearchProviderId() {
      return myId
    }

    @Override
    String getGroupName() {
      return myId
    }

    @Override
    int getSortWeight() {
      return mySortWeight
    }

    @Override
    boolean showInFindResults() {
      return false
    }

    @Override
    boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText) {
      return false
    }

    @Override
    ListCellRenderer<? super Object> getElementsRenderer() {
      return new DefaultListCellRenderer()
    }

    @Override
    Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
      return null
    }
  }

  private static class StubAction extends AnAction{
    StubAction(String text) {
      super(text)
    }

    @Override
    void actionPerformed(@NotNull AnActionEvent e) {}
  }

  private static class SEParam {
    final boolean defaultValue
    final Supplier<Boolean> getter
    final Consumer<Boolean> setter

    SEParam(Supplier<Boolean> getter, Consumer<Boolean> setter) {
      this.getter = getter
      this.setter = setter

      defaultValue = getter.get()
    }

    boolean get() {
      return getter.get()
    }

    void set(boolean val) {
      if (get() == val) return
      setter.consume(val)
    }

    void reset() {
      set(defaultValue)
    }
  }
}
