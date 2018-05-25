// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase 
/**
 * @author peter
 */
class GotoActionTest extends LightCodeInsightFixtureTestCase {

  void "test shorter actions first despite ellipsis"() {
    def pattern = 'Rebas'
    def fork = 'Rebase my GitHub fork'
    def rebase = 'Rebase...'
    def items = [matchedValue(fork, pattern),
                 matchedValue(rebase, pattern)].sort()
    assert [rebase, fork] == items.collect { it.valueText }
  }

  void "test sort by match mode"() {
    def pattern = 'by'
    def byName = 'By Name'
    def byDesc = 'By Desc'
    def items = [matchedValue(byName, pattern),
                 matchedValue(byDesc, pattern, GotoActionModel.MatchMode.DESCRIPTION)].sort()
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
    def items = [matchedValue(boom, pattern), matchedValue(aardvark, pattern), matchedValue(copy, pattern),
                 matchedValue(eclaire, pattern), matchedValue(deaf, pattern), matchedValue(cut, pattern), matchedValue(c, pattern)].sort()
    assert [c, copy, cut, aardvark, eclaire, boom, deaf] == items.collect { it.valueText }
  }

  void "test match action by parent and grandparent group name"() {
    def extractMethod = ActionManager.instance.getAction("ExtractMethod")
    assert actionMatches('method', extractMethod) == GotoActionModel.MatchMode.NAME
    assert actionMatches('extract method', extractMethod) == GotoActionModel.MatchMode.GROUP
    assert actionMatches('refactor method', extractMethod) == GotoActionModel.MatchMode.GROUP
  }

  def actionMatches(String pattern, AnAction action) {
    return new GotoActionModel(project, null, null).actionMatches(pattern, GotoActionItemProvider.buildMatcher(pattern), action)
  }
  
  def matchedValue(String fork, String pattern) {
    matchedValue(fork, pattern, GotoActionModel.MatchMode.NAME)
  }

  def matchedValue(String fork, String pattern, GotoActionModel.MatchMode mode) {
    new GotoActionModel.MatchedValue(createAction(fork, mode), pattern)
  }

  def createAction(String text, GotoActionModel.MatchMode mode) {
    def action = new AnAction(text) {
      @Override
      void actionPerformed(AnActionEvent e) {
      }
    }
    new GotoActionModel.ActionWrapper(action, "", mode, DataContext.EMPTY_CONTEXT, new GotoActionModel(project, null, null))
  }
}
