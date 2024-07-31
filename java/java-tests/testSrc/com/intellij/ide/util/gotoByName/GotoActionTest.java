// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper;
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue;
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValueType;
import com.intellij.java.navigation.ChooseByNameTest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.assertj.core.api.SoftAssertions;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;

public class GotoActionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final DataKey<Boolean> SHOW_HIDDEN_KEY = DataKey.create("GotoActionTest.DataKey");
  private static final Comparator<MatchedValue> MATCH_COMPARATOR = MatchedValue::compareWeights;
  private static final BiPredicate<MatchedValue, MatchedValue> MATCH_EQUALITY = MatchedValue::equals;

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  public void testShorterActionsFirstDespiteEllipsis() {
    String pattern = "Rebas";
    String fork = "Sync Fork";
    String rebase = "Rebase...";
    List<MatchedValue> items = Arrays.asList(matchedAction(fork, pattern), matchedAction(rebase, pattern));
    items.sort(MATCH_COMPARATOR);
    assertEquals(List.of(rebase, fork), ContainerUtil.map(items, item -> item.getValueText()));
  }

  public void testSortByMatchMode() {
    String pattern = "by";
    String byName = "By Name";
    String byDesc = "By Desc";
    List<MatchedValue> items = Arrays.asList(matchedAction(byName, pattern), matchedAction(byDesc, pattern, MatchMode.DESCRIPTION, true));
    items.sort(MATCH_COMPARATOR);
    assertEquals(List.of(byName, byDesc), ContainerUtil.map(items, item -> item.getValueText()));
  }

  public void testSortByDegree() {
    String pattern = "c";
    String copy = "Copy";
    String aardvark = "Aardvarck";
    String boom = "Boom";
    String deaf = "deaf";
    String eclaire = "eclaire";
    String cut = "Cut";
    String c = "c";
    List<MatchedValue> items = Arrays.asList(matchedAction(boom, pattern),
                                             matchedAction(aardvark, pattern),
                                             matchedAction(copy, pattern),
                                             matchedAction(eclaire, pattern),
                                             matchedAction(deaf, pattern),
                                             matchedAction(cut, pattern),
                                             matchedAction(c, pattern));
    items.sort(MATCH_COMPARATOR);
    assertEquals(List.of(c, copy, cut, aardvark, eclaire, boom, deaf), ContainerUtil.map(items, item -> item.getValueText()));
  }

  public void testMatchActionByParentAndGrandparentGroupName() {
    AnAction extractMethod = ActionManager.getInstance().getAction("IntroduceVariable");
    assertEquals(MatchMode.NAME, actionMatches("variable", extractMethod));
    assertEquals(MatchMode.GROUP, actionMatches("extract variable", extractMethod));
    assertEquals(MatchMode.GROUP, actionMatches("refactor variable", extractMethod));
  }

  public void testNoLowercaseCamelHumpActionDescriptionMatch() {
    AnAction action = ActionManager.getInstance().getAction("InvalidateCaches");
    assertEquals(MatchMode.NAME, actionMatches("invalid", action));
    assertEquals(MatchMode.NAME, actionMatches("invalidate caches", action));
    assertEquals(MatchMode.NAME, actionMatches("cache invalid", action));
    assertEquals(MatchMode.DESCRIPTION, actionMatches("rebuild of all caches", action));
    assertEquals(MatchMode.NAME, actionMatches("invcach", action));
  }

  public void testFixingLayoutMatch() {
    AnAction action = ActionManager.getInstance().getAction("InvalidateCaches");
    assertEquals(MatchMode.NAME, actionMatches("штм", action));
    assertEquals(MatchMode.NAME, actionMatches("штм сфср", action));
    assertEquals(MatchMode.NAME, actionMatches("привет мир", new DumbAwareAction("привет, мир") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    }));
  }

  public void testCamelCaseTextInActionNames() {
    List<OptionDescription> options =
      List.of(new OptionDescription("CamelCase option", null, null),
              new OptionDescription("non camel case option", null, null),
              new OptionDescription("just another option", null, null),
              new TestBooleanOption("Boolean CamelCase option"),
              new TestBooleanOption("Boolean non camel case option"),
              new TestBooleanOption("Just another boolean option"));

    OptionsSearchTopHitProvider.ApplicationLevelProvider provider = new OptionsSearchTopHitProvider.ApplicationLevelProvider() {
      @NotNull
      @Override
      public String getId() {
        return "testprovider";
      }

      @NotNull
      @Override
      public Collection<OptionDescription> getOptions() {
        return options;
      }
    };

    List<Object> topHits = new ArrayList<>(2);
    provider.consumeTopHits("/testprovider CamelCase", o -> topHits.add(o), getProject());
    assertEquals(List.of(options.get(0), options.get(3)), topHits);
  }

  public void testTopHitCamelCaseActionsFoundInLowerCase() {
    ExtensionTestUtil.maskExtensions(GotoActionAliasMatcher.EP_NAME, Collections.emptyList(), getTestRootDisposable());
    AnAction action1 = createAction("CamelCase Name", "none");
    AnAction action2 = createAction("Simple Name", "CamelCase description");
    AnAction action3 = createAction("Prefix-CamelCase Name", "none");
    AnAction action4 = createAction("Non Camel Case Name", "none");
    AnAction action5 = createAction("Dash Camel-Case Name", "none");
    String pattern = "camelcase";
    assertEquals(MatchMode.NAME, actionMatches(pattern, action1));
    assertEquals(MatchMode.DESCRIPTION, actionMatches(pattern, action2));
    assertEquals(MatchMode.NAME, actionMatches(pattern, action3));
    assertEquals(MatchMode.NAME, actionMatches(pattern, action4));
    assertEquals(MatchMode.NAME, actionMatches(pattern, action5));
  }

  public void testTopHitCamelCaseOptionsFoundInLowerCase() {
    ExtensionTestUtil.maskExtensions(GotoActionAliasMatcher.EP_NAME, Collections.emptyList(), getTestRootDisposable());
    OptionDescription option1 = new OptionDescription("CamelCase Name", null, null);
    OptionDescription option2 = new OptionDescription("Simple Name", null, null);
    OptionDescription option3 = new OptionDescription("Prefix-CamelCase Name", null, null);
    OptionDescription option4 = new OptionDescription("Non Camel Case Name", null, null);
    OptionDescription option5 = new OptionDescription("Dash Camel-Case Name", null, null);
    String pattern = "camelcase";
    assertTrue(optionMatches(pattern, option1));
    assertFalse(optionMatches(pattern, option2));
    assertTrue(optionMatches(pattern, option3));
    assertFalse(optionMatches(pattern, option4));
    assertFalse(optionMatches(pattern, option5));
  }

  public void testMatchedValueComparator() throws ExecutionException, InterruptedException, TimeoutException {
    String pattern = "Text";
    List<String> names = List.of("Text", "Text completion", "Completion Text", "Add text", "Retextovize mapping", "Value", "A", "Z");

    ArrayList<MatchedValue> items = new ArrayList<>();
    names.forEach(name -> {
      items.add(matchedAction(name, pattern, MatchMode.NAME, true));
      items.add(matchedAction(name, pattern, MatchMode.NAME, false));
      items.add(matchedAction(name, pattern, MatchMode.DESCRIPTION, true));
      items.add(matchedAction(name, pattern, MatchMode.DESCRIPTION, false));
      items.add(matchedOption(name, pattern));
      items.add(matchedOption(name, pattern));
      items.add(matchedBooleanOption(name, pattern));
    });
    
    PlatformTestUtil.assertComparisonContractNotViolated(items, MATCH_COMPARATOR, MATCH_EQUALITY);

    // order can be different on EDT and pooled threads
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> PlatformTestUtil.assertComparisonContractNotViolated(items, MATCH_COMPARATOR, MATCH_EQUALITY)).get(20000, TimeUnit.MILLISECONDS);
  }

  public void testSameActionIsNotReportedTwice() {
    List<String> patterns = List.of("Patch", "Add", "Delete", "Show", "Toggle", "New", "New Class");

    SearchEverywhereContributor<?> contributor = createActionContributor(getProject(), getTestRootDisposable());
    patterns.forEach(pattern -> {
      List<?> result = ChooseByNameTest.calcContributorElements(contributor, pattern);
      Collection<Object> actions =
        ContainerUtil.mapNotNull(result, o -> {
          if (o instanceof MatchedValue matchedValue) {
            Object value = matchedValue.value;
            if (value instanceof ActionWrapper actionWrapper) {
              return actionWrapper.getAction();
            }

            if (value instanceof OptionDescription) {
              return value;
            }
          }

          return null;
        });

      assertEquals(actions.size(), new HashSet<>(actions).size());
    });
  }

  public void testDetectedActionGroups() {
    assertEquals("Images", getPresentableGroupName(getProject(), "Zoom", "Images.Editor.ZoomIn"));
    assertEquals("Search Everywhere", getPresentableGroupName(getProject(), "Next Tab", "SearchEverywhere.NextTab"));
    assertEquals("Window | Editor Tabs", getPresentableGroupName(getProject(), "Next Tab", "NextTab"));
    assertEquals("Tabs", getPresentableGroupName(getProject(), "Next Tab", "NextEditorTab"));
  }

  public void testSameInvisibleGroupsAreIgnored() {
    String pattern = "GotoActionTest.TestAction";

    AnAction testAction = createAction(pattern);
    DefaultActionGroup outerGroup = createActionGroup("Outer", false);
    DefaultActionGroup visibleGroup = createActionGroup("VisibleGroup", false);
    DefaultActionGroup hiddenGroup1 = createActionGroup("A HiddenGroup1", true);
    DefaultActionGroup hiddenGroup2 = createActionGroup("Z HiddenGroup2", true);
    outerGroup.add(hiddenGroup1);
    outerGroup.add(visibleGroup);
    outerGroup.add(hiddenGroup2);
    visibleGroup.add(testAction);
    hiddenGroup1.add(testAction);
    hiddenGroup2.add(testAction);

    runWithGlobalAction(pattern, testAction, () -> 
      runWithMainMenuGroup(outerGroup, () -> {
        assertEquals("Outer | VisibleGroup", getPresentableGroupName(getProject(), pattern, testAction, false));
        assertEquals("Outer | A HiddenGroup1", getPresentableGroupName(getProject(), pattern, testAction, true));

        outerGroup.remove(visibleGroup);

        assertNull(getPresentableGroupName(getProject(), pattern, testAction, false));
        assertEquals("Outer | A HiddenGroup1", getPresentableGroupName(getProject(), pattern, testAction, true));

        outerGroup.remove(hiddenGroup1);

        assertNull(getPresentableGroupName(getProject(), pattern, testAction, false));
        assertEquals("Outer | Z HiddenGroup2", getPresentableGroupName(getProject(), pattern, testAction, true));

        hiddenGroup2.remove(testAction);

        assertNull(getPresentableGroupName(getProject(), pattern, testAction, false));
        assertNull(getPresentableGroupName(getProject(), pattern, testAction, true));
      })
    );
  }

  public void testActionOrderIsStableWithDifferentPresentation() {
    String pattern = "GotoActionTest.TestAction";

    AnAction testAction1 = createAction(pattern);
    AnAction testAction2 = createAction(pattern);
    DefaultActionGroup outerGroup = createActionGroup("Outer", false);
    DefaultActionGroup hiddenGroup = createActionGroup("A Hidden", false);
    DefaultActionGroup visibleGroup1 = createActionGroup("K Visible", true);
    DefaultActionGroup visibleGroup2 = createActionGroup("Z Visible", true);
    outerGroup.add(hiddenGroup);
    outerGroup.add(visibleGroup1);
    outerGroup.add(visibleGroup2);
    visibleGroup1.add(testAction1);
    visibleGroup2.add(testAction2);
    hiddenGroup.add(testAction2);

    runWithGlobalAction(pattern + "1", testAction1, () -> {
      runWithGlobalAction(pattern + "2", testAction2, () -> {
        runWithMainMenuGroup(outerGroup, () -> {
          List<ActionWrapper> order1 = computeWithCustomDataProvider(true, () -> getSortedActionsFromPopup(getProject(), pattern));
          List<ActionWrapper> order2 = computeWithCustomDataProvider(false, () -> getSortedActionsFromPopup(getProject(), pattern));

          assertEquals(order1, order2);
        });
      });
    });
  }

  public void testNavigableSettingsOptionsAppearInResults() {
    SoftAssertions.assertSoftly(softly -> {
      SearchEverywhereContributor<?> contributor = createActionContributor(getProject(), getTestRootDisposable());
      for (String pattern : List.of("support screen readers", "show line numbers", "tab placement")) {
        List<?> elements = ChooseByNameTest.calcContributorElements(contributor, pattern);
        boolean result = false;
        for (Object t : elements) {
          if (isNavigableOption(((MatchedValue)t).value)) {
            result = true;
            break;
          }
        }
        if (!result) {
          softly.fail("Failure for pattern '" + pattern + "' - " + elements);
        }
      }
    });
  }

  public void testUseUpdatedPresentationForMatching() {
    String templateName = "TemplateActionName";
    String updatedName = "UpdatedActionName";
    AnAction testAction = new AnAction(templateName) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) { }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(updatedName);
      }
    };

    SearchEverywhereContributor<?> contributor = createActionContributor(getProject(), getTestRootDisposable());
    runWithGlobalAction("myTestAction", testAction, () -> {
      List<?> result = ChooseByNameTest.calcContributorElements(contributor, "UpdatedActionName");
      assertEquals(1, result.size());
    });
  }

  private static boolean isNavigableOption(Object o) {
    return o instanceof OptionDescription && !(o instanceof BooleanOptionDescription);
  }

  private List<ActionWrapper> getSortedActionsFromPopup(Project project, String pattern) {
    List<ActionWrapper> wrappers = new ArrayList<>(getActionsFromPopup(project, getTestRootDisposable(), pattern));
    wrappers.sort(Comparator.comparing(ActionWrapper::getActionText));
    return wrappers;
  }

  private String getPresentableGroupName(Project project, String pattern, String testActionId) {
    AnAction action = ActionManager.getInstance().getAction(testActionId);
    assertNotNull(action);
    return getPresentableGroupName(project, pattern, action, false);
  }

  private String getPresentableGroupName(Project project, String pattern, AnAction testAction, boolean passFlag) {
    return computeWithCustomDataProvider(passFlag, () -> {
      List<ActionWrapper> result = getActionsFromPopup(project, getTestRootDisposable(), pattern);
      List<ActionWrapper> matches =
        ContainerUtil.findAll(result, wrapper -> wrapper.getAction().equals(testAction));
      if (matches.size() != 1) {
        fail("Matches: " + matches + "\nPopup actions:  " + result.size() + " - " + result);
      }
      
      ActionWrapper wrapper = matches.get(0);
      return wrapper.getGroupName();
    });
  }

  private static void runWithGlobalAction(String id, AnAction action, Runnable task) {
    ActionManager.getInstance().registerAction(id, action);
    try {
      task.run();
    }
    finally {
      ActionManager.getInstance().unregisterAction(id);
    }
  }

  private static void runWithMainMenuGroup(ActionGroup group, Runnable task) {
    DefaultActionGroup mainMenuGroup = (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU);
    mainMenuGroup.add(group);
    try {
      task.run();
    }
    finally {
      mainMenuGroup.remove(group);
    }
  }

  private static <T> T computeWithCustomDataProvider(boolean passHiddenFlag, Computable<T> task) {
    TestApplicationManager.getInstance().setDataProvider(dataId -> {
      return SHOW_HIDDEN_KEY.is(dataId) && passHiddenFlag ? Boolean.TRUE : null;
    });

    try {
      return task.compute();
    }
    finally {
      TestApplicationManager.getInstance().setDataProvider(null);
    }
  }

  private static List<ActionWrapper> getActionsFromPopup(Project project, Disposable parentDisposable, String pattern) {
    SearchEverywhereContributor<?> contributor = createActionContributor(project, parentDisposable);
    return ContainerUtil.mapNotNull(ChooseByNameTest.calcContributorElements(contributor, pattern), e -> {
      return e instanceof MatchedValue matchedValue && matchedValue.value instanceof ActionWrapper actionWrapper ? actionWrapper : null;
    });
  }

  private MatchMode actionMatches(String pattern, AnAction action) {
    Matcher matcher = ActionSearchUtilKt.buildMatcher(pattern);
    GotoActionModel model = new GotoActionModel(getProject(), null, null);
    model.buildGroupMappings();
    return model.actionMatches(pattern, matcher, action);
  }

  private static MatchedValue matchedAction(String text, String pattern, MatchMode mode, boolean isAvailable) {
    return createMatchedAction(createAction(text), pattern, mode, isAvailable);
  }

  private static MatchedValue matchedAction(String text, String pattern) {
    return matchedAction(text, pattern, MatchMode.NAME, true);
  }

  public static MatchedValue createMatchedAction(AnAction action,
                                                 String pattern,
                                                 MatchMode mode,
                                                 boolean isAvailable) {
    Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(isAvailable);
    ActionWrapper wrapper = new ActionWrapper(action, null, mode, presentation);
    return new MatchedValue(wrapper, pattern, MatchedValueType.ACTION);
  }

  public static MatchedValue createMatchedAction(AnAction action, String pattern) {
    return createMatchedAction(action, pattern, MatchMode.NAME, true);
  }

  private static AnAction createAction(String text) {
    return createAction(text, null);
  }

  private static AnAction createAction(String text, String description) {
    AnAction action = new AnAction(text) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    };
    if (description != null) {
      action.getTemplatePresentation().setDescription(description);
    }
    return action;
  }

  private static DefaultActionGroup createActionGroup(String text, boolean hideByDefault) {
    return new DefaultActionGroup(text, true) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(!hideByDefault || e.getData(SHOW_HIDDEN_KEY) != null);
      }
    };
  }

  private static boolean optionMatches(String pattern, OptionDescription optionDescription) {
    return OptionsTopHitProvider.Companion.buildMatcher(pattern).matches(optionDescription.getOption());
  }

  private static MatchedValue matchedOption(String text, String pattern) {
    return new MatchedValue(new OptionDescription(text), pattern, MatchedValueType.OPTION);
  }

  private static MatchedValue matchedBooleanOption(String text, String pattern) {
    BooleanOptionDescription option = new BooleanOptionDescription(text, text) {
      @Override
      public boolean isOptionEnabled() {
        return false;
      }

      @Override
      public void setOptionState(boolean enabled) {
      }
    };
    return new MatchedValue(option, pattern, MatchedValueType.OPTION);
  }

  public static SearchEverywhereContributor<?> createActionContributor(Project project, Disposable parentDisposable) {
    TestActionContributor res = new TestActionContributor(project, null, null);
    res.setShowDisabled(true);
    Disposer.register(parentDisposable, res);
    return res;
  }
  
  private static class TestBooleanOption extends BooleanOptionDescription {
    private TestBooleanOption(String option) {
      super(option, null);
    }

    @Override
    public boolean isOptionEnabled() {
      return true;
    }

    @Override
    public void setOptionState(boolean enabled) {
    }
  }

  private static class TestActionContributor extends ActionSearchEverywhereContributor {
    private TestActionContributor(Project project, Component contextComponent, Editor editor) {
      super(project, contextComponent, editor);
    }

    public void setShowDisabled(boolean val) {
      setMyDisabledActions(val);
    }
  }
}
