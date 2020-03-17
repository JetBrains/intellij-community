// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Assert

import java.util.concurrent.Future

class SearchEverywhereTest extends LightJavaCodeInsightFixtureTestCase {
  SearchEverywhereUI mySearchUI

  @Override
  protected void tearDown() throws Exception {
    if (mySearchUI != null) {
      Disposer.dispose(mySearchUI)
      mySearchUI = null
    }

    super.tearDown()
  }

  void "test switch to external files when nothing is found"() {
    def strBuffer = myFixture.addClass("class StrBuffer{ }")
    def stringBuffer = myFixture.findClass("java.lang.StringBuffer")

    def ui = createTestUI([ChooseByNameTest.createClassContributor(project)])

    def future = ui.findElementsForPattern("StrBuffer")
    if (!waitFor(future)) Assert.fail("Search haven't finished")
    assert future.get() == [strBuffer]

    future = ui.findElementsForPattern("StringBuffer")
    if (!waitFor(future)) Assert.fail("Search haven't finished")
    assert future.get() == [stringBuffer]
  }

  private static boolean waitFor(Future<?> future, long timeout = 30000) {
    def start = System.currentTimeMillis()
    while (!future.isDone()) {
      UIUtil.dispatchAllInvocationEvents()
      if (System.currentTimeMillis() - start > timeout) return false
    }

    return true
  }


  private SearchEverywhereUI createTestUI(List<SearchEverywhereContributor<Object>> contributors) {
    if (mySearchUI != null) Disposer.dispose(mySearchUI)

    mySearchUI = new SearchEverywhereUI(project, contributors)
    mySearchUI.switchToContributor(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)
    return mySearchUI
  }
}
