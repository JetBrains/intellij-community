// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.apiUse;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * This is a base test case for test cases that highlight all the use of API
 * that were introduced in later language levels comparing to the current language level
 *
 * In order to add a new test case:
 * <ol>
 * <li>Go to "inspection/api_usage/data"</li>
 * <li>Add a new file(s) to "inspection/api_usage/data/src" that contains new API. It's better to describe the new API as native methods.</li>
 * <li>Set <code>JAVA_HOME</code> to jdk 1.8. In this case it's possible to redefine JDK's own classes like <code>String</code> or <code>Class</code></li>
 * <li>Invoke "inspection/api_usage/compile.sh". The new class(es) will appear in "inspection/api_usage/data/classes"</li>
 * <li>Add a new junit test class for that that inherits this class</li>
 * </ol>
 */
public abstract class BaseApiUsageTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(Java15APIUsageInspection.class);
  }

  @NotNull
  @Override
  protected final LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return BaseApiUsageTestCase.this.getSdk();
      }

      @Override
      public void configureModule(@NotNull Module module,
                                  @NotNull ModifiableRootModel model,
                                  @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        String dataDir = JavaTestUtil.getJavaTestDataPath() + "/inspection/api_usage/data";
        PsiTestUtil.newLibrary("JDKMock").classesRoot(dataDir + "/classes").addTo(model);
      }
    };
  }

  /**
   * Returns the SDK that introduces the new API
   * @return SDK that introduces new API
   */
  protected abstract @NotNull Sdk getSdk();
  /**
   * Returns the LanguageLevel that accesses the new API
   * @return LanguageLevel that accesses the new API
   */
  protected abstract @NotNull LanguageLevel getLanguageLevel();

  protected final void doTest() {
    IdeaTestUtil.withLevel(myFixture.getModule(), getLanguageLevel(), () ->
      myFixture.testHighlighting(getTestName(false) + ".java"));
  }

}