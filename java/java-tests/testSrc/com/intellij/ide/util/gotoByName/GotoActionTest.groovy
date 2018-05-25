// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchMode
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.java.navigation.ChooseByNameTest
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

/**
 * @author peter
 */
@CompileStatic
class GotoActionTest extends LightCodeInsightFixtureTestCase {
  void "test shorter actions first despite ellipsis"() {
    def pattern = 'Rebas'
    def fork = 'Rebase my GitHub fork'
    def rebase = 'Rebase...'
    def items = [matchedAction(fork, pattern),
                 matchedAction(rebase, pattern)].sort()
    assert [rebase, fork] == items.collect { it.valueText }
  }

  void "test sort by match mode"() {
    def pattern = 'by'
    def byName = 'By Name'
    def byDesc = 'By Desc'
    def items = [matchedAction(byName, pattern),
                 matchedAction(byDesc, pattern, MatchMode.DESCRIPTION)].sort()
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
                 matchedAction(c, pattern)].sort()
    assert [c, copy, cut, aardvark, eclaire, boom, deaf] == items.collect { it.valueText }
  }

  void "test match action by parent and grandparent group name"() {
    def extractMethod = ActionManager.instance.getAction("ExtractMethod")
    assert actionMatches('method', extractMethod) == MatchMode.NAME
    assert actionMatches('extract method', extractMethod) == MatchMode.GROUP
    assert actionMatches('refactor method', extractMethod) == MatchMode.GROUP
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
      items += new MatchedValue(name, pattern)
    }

    PlatformTestUtil.assertComparisonContractNotViolated(items,
                                                         { def item1, def item2 -> (item1 <=> item2) },
                                                         { def item1, def item2 -> item1 == item2 })

    // order can be different on EDT and pooled threads
    ApplicationManager.getApplication().executeOnPooledThread {
      PlatformTestUtil.assertComparisonContractNotViolated(items,
                                                           { def item1, def item2 -> (item1 <=> item2) },
                                                           { def item1, def item2 -> item1 == item2 })
    }.get(20000, TimeUnit.MILLISECONDS)
  }

  void "test same action is not reported twice"() {
    def patterns = ["Patch", "Add", "Delete", "Show", "Toggle"]

    def model = new GotoActionModel(project, null, null)
    def provider = new GotoActionItemProvider(model);

    def popup = ChooseByNamePopup.createPopup(project, model, provider)
    try {
      patterns.forEach { String pattern ->
        def result = ChooseByNameTest.calcPopupElements(popup, pattern, true)
        def actions = result.findResults {
          if (it instanceof MatchedValue) {
            def value = it.value
            if (value instanceof ActionWrapper) {
              return (value as ActionWrapper).action;
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
    finally {
      popup.close(false)
    }
  }

  private def actionMatches(String pattern, AnAction action) {
    return new GotoActionModel(project, null, null).actionMatches(pattern, GotoActionItemProvider.buildMatcher(pattern), action)
  }

  private MatchedValue matchedAction(String text, String pattern, MatchMode mode = MatchMode.NAME, boolean isAvailable = true) {
    return matchedAction(createAction(text), pattern, mode, isAvailable)
  }

  private MatchedValue matchedAction(AnAction action, String pattern, MatchMode mode = MatchMode.NAME, boolean isAvailable = true) {
    def model = new GotoActionModel(project, null, null)
    def wrapper = new ActionWrapper(action, "", mode, DataContext.EMPTY_CONTEXT, model) {
      @Override
      boolean isAvailable() {
        return isAvailable
      }
    }
    new MatchedValue(wrapper, pattern)
  }

  private static AnAction createAction(String text) {
    new AnAction(text) {
      @Override
      void actionPerformed(AnActionEvent e) {
      }
    }
  }

  private static MatchedValue matchedOption(String text, String pattern) {
    return new MatchedValue(new OptionDescription(text), pattern)
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
    return new MatchedValue(option, pattern)
  }
}
