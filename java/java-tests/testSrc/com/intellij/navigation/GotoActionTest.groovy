/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.navigation
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class GotoActionTest extends LightCodeInsightFixtureTestCase {

  public void "test shorter actions first despite ellipsis"() {
    def pattern = 'Rebas'
    def fork = 'Rebase my GitHub fork'
    def rebase = 'Rebase...'
    def items = [new GotoActionModel.MatchedValue(createAction(fork), pattern), new GotoActionModel.MatchedValue(createAction(rebase), pattern)].sort()
    assert [rebase, fork] == items.collect { it.valueText }
  }

  private static GotoActionModel.ActionWrapper createAction(String text) {
    def action = new AnAction(text) {
      @Override
      void actionPerformed(AnActionEvent e) {
      }
    }
    return new GotoActionModel.ActionWrapper(action, "", GotoActionModel.MatchMode.NAME, DataContext.EMPTY_CONTEXT)
  }

}
