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
package test;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import junit.framework.TestCase;
import org.intellij.lang.regexp.RegExpFileType;

public class BaseParseTestcase extends TestCase {
    protected CodeInsightTestFixture myFixture;

    protected void setUp() throws Exception {
        final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
        final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createLightFixtureBuilder();

        final IdeaProjectTestFixture projectFixture = builder.getFixture();
        projectFixture.setUp();

        final CodeInsightTestFixture fixture = fixtureFactory.createCodeInsightFixture(projectFixture);
        fixture.setTestDataPath(getTestDataPath());
        fixture.setUp();

        final Project project = projectFixture.getProject();

        new WriteCommandAction(project) {
            protected void run(Result result) throws Throwable {
                FileTypeManager.getInstance().registerFileType(RegExpFileType.INSTANCE, new String[]{ "regexp" });
            }
        }.execute();

        myFixture = fixture;
    }

    protected String getTestDataPath() {
        return "testData/psi/";
    }

    protected void tearDown() throws Exception {
        myFixture.tearDown();
    }
}
