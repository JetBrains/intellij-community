// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

import static com.intellij.testFramework.PlatformTestUtil.waitForFuture

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
    assert waitForFuture(future, 5000) == [strBuffer]

    future = ui.findElementsForPattern("StringBuffer")
    assert waitForFuture(future, 5000) == [stringBuffer]
  }

  private SearchEverywhereUI createTestUI(List<SearchEverywhereContributor<Object>> contributors) {
    if (mySearchUI != null) Disposer.dispose(mySearchUI)

    mySearchUI = new SearchEverywhereUI(project, contributors)
    mySearchUI.switchToContributor(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)
    return mySearchUI
  }
}
