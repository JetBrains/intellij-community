// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.java.navigation.ChooseByNameTest
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.CollectConsumer
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

import java.awt.*
import java.util.List
import java.util.concurrent.TimeUnit
import java.util.function.BiPredicate

/**
 * @author peter
 */
@CompileStatic
class GotoActionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final DataKey<Boolean> SHOW_HIDDEN_KEY = DataKey.create("GotoActionTest.DataKey")
  private static final Comparator<MatchedValue> MATCH_COMPARATOR =
    { MatchedValue item1, MatchedValue item2 -> return item1.compareWeights(item2) } as Comparator<MatchedValue>
  private static final BiPredicate<MatchedValue, MatchedValue> MATCH_EQUALITY =
    { MatchedValue item1, MatchedValue item2 -> item1 == item2 } as BiPredicate<MatchedValue, MatchedValue>

  void "test shorter actions first despite ellipsis"() {
    def pattern = 'Rebas'
    def fork = 'Sync Fork'
    def rebase = 'Rebase...'
    def items = [matchedAction(fork, pattern),
                 matchedAction(rebase, pattern)].toSorted(MATCH_COMPARATOR)
    assert [rebase, fork] == items.collect { it.valueText }
  }

  void "test sort by match mode"() {
    def pattern = 'by'
    def byName = 'By Name'
    def byDesc = 'By Desc'
    def items = [matchedAction(byName, pattern),
                 matchedAction(byDesc, pattern, MatchMode.DESCRIPTION)].toSorted(MATCH_COMPARATOR)
    assert [byName, byDesc] == items.collect { it.valueText }
  }

  void "test sort by degree"() {
    def pattern = 'c'
    def copy = 'Copy'
    def aardvark = 'Aardvarck'
    def boom = 'Boom'
    def deaf = 'deaf'
    def eclaire = 'eclaire'
    def cut = 'Cut'
    def c = 'c'
    def items = [matchedAction(boom, pattern),
                 matchedAction(aardvark, pattern),
                 matchedAction(copy, pattern),
                 matchedAction(eclaire, pattern),
                 matchedAction(deaf, pattern),
                 matchedAction(cut, pattern),
                 matchedAction(c, pattern)].toSorted(MATCH_COMPARATOR)
    assert [c, copy, cut, aardvark, eclaire, boom, deaf] == items.collect { it.valueText }
  }

  void "test match action by parent and grandparent group name"() {
    def extractMethod = ActionManager.instance.getAction("IntroduceVariable")
    assert actionMatches('variable', extractMethod) == MatchMode.NAME
    assert actionMatches('extract variable', extractMethod) == MatchMode.GROUP
    assert actionMatches('refactor variable', extractMethod) == MatchMode.GROUP
  }

  void "test no lowercase camel-hump action description match"() {
    def action = ActionManager.instance.getAction("InvalidateCaches")
    assert actionMatches('invalid', action) == MatchMode.NAME
    assert actionMatches('invalidate caches', action) == MatchMode.NAME
    assert actionMatches('cache invalid', action) == MatchMode.NAME
    assert actionMatches('rebuild of all caches', action) == MatchMode.DESCRIPTION
    assert actionMatches('invcach', action) == MatchMode.NAME
  }

  void "test fixing layout match"() {
    def action = ActionManager.instance.getAction("InvalidateCaches")
    assert actionMatches('штм', action) == MatchMode.NAME
    assert actionMatches('штм сфср', action) == MatchMode.NAME
    assert actionMatches('привет мир', new DumbAwareAction("привет, мир") {
      @Override
      void actionPerformed(@NotNull AnActionEvent e) {
      }
    }) == MatchMode.NAME
  }

  void "test CamelCase text in action names"() {
    def options = [
      new OptionDescription("CamelCase option", null, null),
      new OptionDescription("non camel case option", null, null),
      new OptionDescription("just another option", null, null),
      new TestBooleanOption("Boolean CamelCase option"),
      new TestBooleanOption("Boolean non camel case option"),
      new TestBooleanOption("Just another boolean option"),
    ]

    OptionsSearchTopHitProvider.ApplicationLevelProvider provider = new OptionsSearchTopHitProvider.ApplicationLevelProvider() {
      @NotNull
      @Override
      String getId() {
        return "testprovider"
      }

      @NotNull
      @Override
      Collection<OptionDescription> getOptions() {
        return options
      }
    }

    def consumer = new CollectConsumer<Object>()
    provider.consumeTopHits("/testprovider CamelCase", consumer, project)
    assert consumer.getResult() == [options[0], options[3]]
  }

  void "test TopHit CamelCase actions found in lower case"() {
    ExtensionTestUtil.maskExtensions(GotoActionAliasMatcher.EP_NAME, Collections.emptyList(), testRootDisposable)
    def action1 = createAction("CamelCase Name", "none")
    def action2 = createAction("Simple Name", "CamelCase description")
    def action3 = createAction("Prefix-CamelCase Name", "none")
    def action4 = createAction("Non Camel Case Name", "none")
    def action5 = createAction("Dash Camel-Case Name", "none")
    def pattern = "camelcase"
    assert actionMatches(pattern, action1) == MatchMode.NAME
    assert actionMatches(pattern, action2) == MatchMode.DESCRIPTION
    assert actionMatches(pattern, action3) == MatchMode.NAME
    assert actionMatches(pattern, action4) == MatchMode.NAME
    assert actionMatches(pattern, action5) == MatchMode.NAME
  }

  void "test TopHit CamelCase options found in lower case"() {
    ExtensionTestUtil.maskExtensions(GotoActionAliasMatcher.EP_NAME, Collections.emptyList(), testRootDisposable)
    def option1 = new OptionDescription("CamelCase Name", null, null)
    def option2 = new OptionDescription("Simple Name", null, null)
    def option3 = new OptionDescription("Prefix-CamelCase Name", null, null)
    def option4 = new OptionDescription("Non Camel Case Name", null, null)
    def option5 = new OptionDescription("Dash Camel-Case Name", null, null)
    def pattern = "camelcase"
    assert optionMatches(pattern, option1)
    assert !optionMatches(pattern, option2)
    assert optionMatches(pattern, option3)
    assert !optionMatches(pattern, option4)
    assert !optionMatches(pattern, option5)
  }

  private static class TestBooleanOption extends BooleanOptionDescription {

    TestBooleanOption(String option) {
      super(option, null)
    }

    @Override
    boolean isOptionEnabled() {
      return true
    }

    @Override
    void setOptionState(boolean enabled) {
    }
  }

  void "test matched value comparator"() {
    def pattern = 'Text'
    def names = ['Text', 'Text completion', 'Completion Text', 'Add text', 'Retextovize mapping', 'Value', 'A', 'Z']

    def items = new ArrayList<MatchedValue>()
    names.forEach { String name ->
      items += matchedAction(name, pattern, MatchMode.NAME, true)
      items += matchedAction(name, pattern, MatchMode.NAME, false)
      items += matchedAction(name, pattern, MatchMode.DESCRIPTION, true)
      items += matchedAction(name, pattern, MatchMode.DESCRIPTION, false)
      items += matchedOption(name, pattern)
      items += matchedOption(name, pattern)
      items += matchedBooleanOption(name, pattern)
    }

    PlatformTestUtil.assertComparisonContractNotViolated(items, MATCH_COMPARATOR, MATCH_EQUALITY)

    // order can be different on EDT and pooled threads
    ApplicationManager.getApplication().executeOnPooledThread {
      PlatformTestUtil.assertComparisonContractNotViolated(items, MATCH_COMPARATOR, MATCH_EQUALITY)
    }.get(20000, TimeUnit.MILLISECONDS)
  }

  void "test same action is not reported twice"() {
    def patterns = ["Patch", "Add", "Delete", "Show", "Toggle", "New", "New Class"]

    def contributor = createActionContributor(project, testRootDisposable)
    patterns.forEach { String pattern ->
      def result = ChooseByNameTest.calcContributorElements(contributor, pattern)
      def actions = result.findResults {
        if (it instanceof MatchedValue) {
          def value = it.value
          if (value instanceof ActionWrapper) {
            return (value as ActionWrapper).action
          }
          if (value instanceof OptionDescription) {
            return value
          }
        }
        return null
      }
      assert actions.size() == actions.toSet().size()
    }
  }

  void "test detected action groups"() {
    assert getPresentableGroupName(project, "Zoom", "Images.Editor.ZoomIn", false) == "Images"
    assert getPresentableGroupName(project, "Next Tab", "SearchEverywhere.NextTab", false) == "Search Everywhere"
    assert getPresentableGroupName(project, "Next Tab", "NextTab", false) == "Window | Editor Tabs"
    assert getPresentableGroupName(project, "Next Tab", "NextEditorTab", false) == "Tabs"
  }

  void "test same invisible groups are ignored"() {
    def pattern = "GotoActionTest.TestAction"

    def testAction = createAction(pattern)
    def outerGroup = createActionGroup("Outer", false)
    def visibleGroup = createActionGroup("VisibleGroup", false)
    def hiddenGroup1 = createActionGroup("A HiddenGroup1", true)
    def hiddenGroup2 = createActionGroup("Z HiddenGroup2", true)
    outerGroup.add(hiddenGroup1)
    outerGroup.add(visibleGroup)
    outerGroup.add(hiddenGroup2)
    visibleGroup.add(testAction)
    hiddenGroup1.add(testAction)
    hiddenGroup2.add(testAction)

    runWithGlobalAction(pattern, testAction) {
      runWithMainMenuGroup(outerGroup) {
        assert getPresentableGroupName(project, pattern, testAction, false) == "Outer | VisibleGroup"
        assert getPresentableGroupName(project, pattern, testAction, true) == "Outer | A HiddenGroup1"

        outerGroup.remove(visibleGroup)

        assert getPresentableGroupName(project, pattern, testAction, false) == null
        assert getPresentableGroupName(project, pattern, testAction, true) == "Outer | A HiddenGroup1"

        outerGroup.remove(hiddenGroup1)

        assert getPresentableGroupName(project, pattern, testAction, false) == null
        assert getPresentableGroupName(project, pattern, testAction, true) == "Outer | Z HiddenGroup2"

        hiddenGroup2.remove(testAction)

        assert getPresentableGroupName(project, pattern, testAction, false) == null
        assert getPresentableGroupName(project, pattern, testAction, true) == null
      }
    }
  }

  void "test action order is stable with different presentation"() {
    def pattern = "GotoActionTest.TestAction"

    def testAction1 = createAction(pattern)
    def testAction2 = createAction(pattern)
    def outerGroup = createActionGroup("Outer", false)
    def hiddenGroup = createActionGroup("A Hidden", false)
    def visibleGroup1 = createActionGroup("K Visible", true)
    def visibleGroup2 = createActionGroup("Z Visible", true)
    outerGroup.add(hiddenGroup)
    outerGroup.add(visibleGroup1)
    outerGroup.add(visibleGroup2)
    visibleGroup1.add(testAction1)
    visibleGroup2.add(testAction2)
    hiddenGroup.add(testAction2)

    runWithGlobalAction(pattern + "1", testAction1) {
      runWithGlobalAction(pattern + "2", testAction2) {
        runWithMainMenuGroup(outerGroup) {
          def order1 = computeWithCustomDataProvider(true) {
            getSortedActionsFromPopup(project, pattern)
          }

          def order2 = computeWithCustomDataProvider(false) {
            getSortedActionsFromPopup(project, pattern)
          }

          assert order1 == order2
        }
      }
    }
  }

  void "test navigable settings options appear in results"() {
    def contributor = createActionContributor(project, testRootDisposable)
    def patterns = [
      "support screen readers",
      "show line numbers",
      "tab placement"
    ]

    patterns.forEach { String pattern ->
      def elements = ChooseByNameTest.calcContributorElements(contributor, pattern)
      assert elements.any { matchedValue -> isNavigableOption(((MatchedValue)matchedValue).value)
      }
    }
  }

  private static boolean isNavigableOption(Object o) {
    return o instanceof OptionDescription && !(o instanceof BooleanOptionDescription)
  }


  private List<ActionWrapper> getSortedActionsFromPopup(Project project, String pattern) {
    def wrappers = getActionsFromPopup(project, testRootDisposable, pattern)
    wrappers.every { it.getPresentation() } // update best group name
    wrappers.sort()
    return wrappers
  }

  private String getPresentableGroupName(Project project, String pattern, String testActionId, boolean passFlag) {
    def action = ActionManager.instance.getAction(testActionId)
    assert action != null
    return getPresentableGroupName(project, pattern, action, passFlag)
  }

  private String getPresentableGroupName(Project project, String pattern, AnAction testAction, boolean passFlag) {
    return computeWithCustomDataProvider(passFlag) {
      def result = getActionsFromPopup(project, testRootDisposable, pattern)
      def matches = result.findAll { it.action == testAction }
      if (matches.size() != 1) {
        fail("Matches: " + matches + "\nPopup actions:  " + result.size() + " - " + result)
      }

      ActionWrapper wrapper = matches[0]
      wrapper.getPresentation() // update before show
      return wrapper.groupName
    }
  }

  private static void runWithGlobalAction(String id, AnAction action, Runnable task) {
    ActionManager.instance.registerAction(id, action)
    try {
      task.run()
    }
    finally {
      ActionManager.instance.unregisterAction(id)
    }
  }

  private static void runWithMainMenuGroup(ActionGroup group, Runnable task) {
    def mainMenuGroup = (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU)
    mainMenuGroup.add(group)
    try {
      task.run()
    }
    finally {
      mainMenuGroup.remove(group)
    }
  }

  private static <T> T computeWithCustomDataProvider(passHiddenFlag, Computable<T> task) {
    TestApplicationManager.getInstance().setDataProvider(new DataProvider() {
      @Override
      Object getData(@NotNull @NonNls String dataId) {
        if (SHOW_HIDDEN_KEY.is(dataId) && passHiddenFlag) return Boolean.TRUE
        return null
      }
    })

    try {
      return task.compute()
    }
    finally {
      TestApplicationManager.getInstance().setDataProvider(null)
    }
  }

  private static List<ActionWrapper> getActionsFromPopup(Project project, Disposable parentDisposable, String pattern) {
    def contributor = createActionContributor(project, parentDisposable)
    return ChooseByNameTest.calcContributorElements(contributor, pattern).findResults {
      if (it instanceof MatchedValue && it.value instanceof ActionWrapper) {
        return it.value as ActionWrapper
      }
      return null
    } as List<ActionWrapper>
  }

  private def actionMatches(String pattern, AnAction action) {
    def matcher = GotoActionItemProvider.buildMatcher(pattern)
    def model = new GotoActionModel(project, null, null)
    model.buildGroupMappings()
    return model.actionMatches(pattern, matcher, action)
  }

  private MatchedValue matchedAction(String text, String pattern, MatchMode mode = MatchMode.NAME, boolean isAvailable = true) {
    return createMatchedAction(project, createAction(text), pattern, mode, isAvailable)
  }

  static MatchedValue createMatchedAction(Project project, AnAction action, String pattern, MatchMode mode = MatchMode.NAME, boolean isAvailable = true) {
    def model = new GotoActionModel(project, null, null)
    def wrapper = new ActionWrapper(action, null, mode, model) {
      @Override
      boolean isAvailable() {
        return isAvailable
      }
    }
    new MatchedValue(wrapper, pattern, GotoActionModel.MatchedValueType.ACTION)
  }

  private static AnAction createAction(String text, String description = null) {
    def action = new AnAction(text) {
      @Override
      void actionPerformed(@NotNull AnActionEvent e) {
      }
    }
    if (description != null) {
      action.getTemplatePresentation().setDescription(description)
    }

    return action
  }

  private static DefaultActionGroup createActionGroup(String text, boolean hideByDefault) {
    new DefaultActionGroup(text, true) {
      @Override
      void update(@NotNull AnActionEvent e) {
        e.presentation.setVisible(!hideByDefault || Boolean.valueOf(e.getData(SHOW_HIDDEN_KEY)))
      }
    }
  }

  private static boolean optionMatches(String pattern, OptionDescription optionDescription) {
    def matcher = OptionsTopHitProvider.buildMatcher(pattern)
    return matcher.matches(optionDescription.getOption())
  }


  private static MatchedValue matchedOption(String text, String pattern) {
    return new MatchedValue(new OptionDescription(text), pattern, GotoActionModel.MatchedValueType.OPTION)
  }

  private static MatchedValue matchedBooleanOption(String text, String pattern) {
    def option = new BooleanOptionDescription(text, text) {
      @Override
      boolean isOptionEnabled() {
        return false
      }

      @Override
      void setOptionState(boolean enabled) {
      }
    }
    return new MatchedValue(option, pattern, GotoActionModel.MatchedValueType.OPTION)
  }

  static SearchEverywhereContributor<?> createActionContributor(Project project, Disposable parentDisposable) {
    TestActionContributor res = new TestActionContributor(project, null, null)
    res.setShowDisabled(true)
    Disposer.register(parentDisposable, res)
    return res
  }

  private static class TestActionContributor extends ActionSearchEverywhereContributor {
    TestActionContributor(Project project, Component contextComponent, Editor editor) {
      super(project, contextComponent, editor)
    }

    void setShowDisabled(boolean val) {
      myDisabledActions = val
    }
  }
}
