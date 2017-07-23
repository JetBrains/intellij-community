/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;

public class FindUIHelperTest extends LightPlatformTestCase {
  private static final Runnable STUB = () -> {
  };

  private FindUIHelper myHelper;
  private FindManagerImpl myFindManager;
  private FindUI myUICopy;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFindManager = ((FindManagerImpl)FindManager.getInstance(getProject()));
    FindModel findModel = FindManager.getInstance(getProject()).getFindInProjectModel();
    myHelper = new FindUIHelper(getProject(), findModel, STUB);
    myUICopy = myHelper.myUI;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myHelper);
      assertTrue(Disposer.isDisposed(myUICopy.getDisposable()));
      assertNull(myHelper.myUI);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBasic() {
    assertFalse(myHelper.getModel().isOpenInNewTabEnabled());
    boolean initialSeparateViewState = myHelper.isUseSeparateView();
    boolean initialSkipResultsWithOneUsage = myHelper.isSkipResultsWithOneUsage();
    try {
      myHelper.setUseSeparateView(!initialSeparateViewState);
      fail("There should be an exception");
    }
    catch (IllegalStateException e) {
      //it's ok
      myHelper.getModel().setOpenInNewTabEnabled(true);
      myHelper.setUseSeparateView(!initialSeparateViewState);
    }
    assertNotSame(initialSeparateViewState, myHelper.isUseSeparateView());
    myHelper.setUseSeparateView(initialSeparateViewState);
    assertSame(initialSeparateViewState, myHelper.isUseSeparateView());

    myHelper.setSkipResultsWithOneUsage(!initialSkipResultsWithOneUsage);
    assertNotSame(initialSkipResultsWithOneUsage, myHelper.isSkipResultsWithOneUsage());

    myHelper.setSkipResultsWithOneUsage(initialSkipResultsWithOneUsage);
    assertSame(initialSkipResultsWithOneUsage, myHelper.isSkipResultsWithOneUsage());

    myFindManager.changeGlobalSettings(myHelper.getModel());

    FindModel findModel = FindManager.getInstance(getProject()).getFindInProjectModel();
    myHelper.setModel(findModel);
    
    assertSame(initialSeparateViewState, myHelper.isUseSeparateView());
    assertSame(initialSkipResultsWithOneUsage, myHelper.isSkipResultsWithOneUsage());
  }
}
