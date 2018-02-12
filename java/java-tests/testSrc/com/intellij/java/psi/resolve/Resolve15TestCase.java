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
package com.intellij.java.psi.resolve;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ResolveTestCase;

/**
 * @author ven
 */
public abstract class Resolve15TestCase extends ResolveTestCase {
  private LanguageLevel myOldLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_5, getModule(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }
}
