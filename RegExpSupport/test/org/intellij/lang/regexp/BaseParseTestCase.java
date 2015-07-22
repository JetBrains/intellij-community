/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class BaseParseTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createFixtureBuilder(getName());

    myFixture = fixtureFactory.createCodeInsightFixture(builder.getFixture());
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();

    final Project project = myFixture.getProject();

    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        FileTypeManager.getInstance().registerFileType(RegExpFileType.INSTANCE, new String[]{"regexp"});
      }
    }.execute();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  public static String getTestDataRoot() {
    String homePath = PathManager.getHomePath();
    File candidate = new File(homePath, "community/RegExpSupport");
    if (candidate.isDirectory()) {
      return new File(candidate, "testData").getPath();
    }
    return new File(homePath, "RegExpSupport/testData").getPath();
  }

  protected String getTestDataPath() {
    return getTestDataRoot() + "/psi";
  }
}
