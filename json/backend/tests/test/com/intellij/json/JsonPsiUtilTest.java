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

import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Mikhail Golubev
 */
public class JsonPsiUtilTest extends JsonTestCase {
  public void testAddPropertyInEmptyLiteral() {
    checkAddProperty("{}", "{\"foo\": null}", true);
  }
  
  public void testAddPropertyInEmptyUnclosedLiteral() {
    checkAddProperty("{", "{\"foo\": null", true);
  }

  public void testAddPropertyFirst() {
    checkAddProperty("{\"bar\": 42}", "{\"foo\": null, \"bar\": 42}", true);
  }
  
  public void testAddPropertyLast() {
    checkAddProperty("{\"bar\": 42}", "{\"bar\": 42, \"foo\": null}", false);
  }

  private void checkAddProperty(@NotNull String before, @NotNull String after, final boolean first) {
    getCustomCodeStyleSettings().OBJECT_WRAPPING = CommonCodeStyleSettings.DO_NOT_WRAP;
    myFixture.configureByText(JsonFileType.INSTANCE, before);
    final PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    final JsonObject jsonObject = PsiTreeUtil.getParentOfType(atCaret, JsonObject.class);
    assertNotNull(jsonObject);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      JsonPsiUtil.addProperty(jsonObject, new JsonElementGenerator(getProject()).createProperty("foo", "null"), first);
    });
    myFixture.checkResult(after);
  }

  public void testGetOtherSiblingPropertyNames() {
    myFixture.configureByText(JsonFileType.INSTANCE, "{\"firs<caret>t\" : 1, \"second\" : 2}");
    PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    JsonProperty property = PsiTreeUtil.getParentOfType(atCaret, JsonProperty.class);
    assertNotNull(property);
    assertEquals(Collections.singleton("second"), JsonPsiUtil.getOtherSiblingPropertyNames(property));
  }
}
