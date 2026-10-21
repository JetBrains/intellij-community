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
package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.json.JsonTestUtil.getCustomCodeStyleSettings;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonPsiUtilTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
  }

  @Test
  public void testAddPropertyInEmptyLiteral() {
    checkAddProperty("{}", "{\"foo\": null}", true);
  }
  
  @Test
  public void testAddPropertyInEmptyUnclosedLiteral() {
    checkAddProperty("{", "{\"foo\": null", true);
  }

  @Test
  public void testAddPropertyFirst() {
    checkAddProperty("{\"bar\": 42}", "{\"foo\": null, \"bar\": 42}", true);
  }
  
  @Test
  public void testAddPropertyLast() {
    checkAddProperty("{\"bar\": 42}", "{\"bar\": 42, \"foo\": null}", false);
  }

  private void checkAddProperty(@NotNull String before, @NotNull String after, final boolean first) {
    getCustomCodeStyleSettings(myFixture.getProject()).OBJECT_WRAPPING = CommonCodeStyleSettings.DO_NOT_WRAP;
    myFixture.configureByText(JsonFileType.INSTANCE, before);
    final JsonObject jsonObject = ReadAction.computeBlocking(() -> {
      final PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      return PsiTreeUtil.getParentOfType(atCaret, JsonObject.class);
    });
    assertNotNull(jsonObject);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      JsonPsiUtil.addProperty(jsonObject, new JsonElementGenerator(myFixture.getProject()).createProperty("foo", "null"), first);
    });
    myFixture.checkResult(after);
  }

  @Test
  public void testGetOtherSiblingPropertyNames() {
    myFixture.configureByText(JsonFileType.INSTANCE, "{\"firs<caret>t\" : 1, \"second\" : 2}");
    ReadAction.runBlocking(() -> {
      PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      JsonProperty property = PsiTreeUtil.getParentOfType(atCaret, JsonProperty.class);
      assertNotNull(property);
      assertEquals(Collections.singleton("second"), JsonPsiUtil.getOtherSiblingPropertyNames(property));
    });
  }
}
