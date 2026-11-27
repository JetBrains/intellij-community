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
package com.intellij.java.psi.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PropertyUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());

  @Test
  public void testSuggestGetterName() {
    ReadAction.runBlocking(() -> {
      assertEquals("isValid", PropertyUtilBase.suggestGetterName("valid", getType("boolean")));
      assertEquals("getValid", PropertyUtilBase.suggestGetterName("valid", getType("Object")));
      assertEquals("isValid", PropertyUtilBase.suggestGetterName(createField("valid", "boolean")));
      assertEquals("getValid", PropertyUtilBase.suggestGetterName(createField("valid", "Object")));

      assertEquals("getURL", PropertyUtilBase.suggestGetterName("URL", getType("Object")));
      assertEquals("getURL", PropertyUtilBase.suggestGetterName(createField("URL", "Object")));

      assertEquals("isaURL", PropertyUtilBase.suggestGetterName(createField("aURL", "boolean")));
      assertEquals("getaURL", PropertyUtilBase.suggestGetterName(createField("aURL", "Object")));
      assertEquals("getBool", PropertyUtilBase.suggestGetterName(createField("bool", "java.lang.Boolean")));
    });
  }

  @Test
  public void testSuggestSetterName() {
    ReadAction.runBlocking(() -> {
      assertEquals("setValid", PropertyUtilBase.suggestSetterName("valid"));
      assertEquals("setValid", PropertyUtilBase.suggestSetterName(createField("valid", "Object")));

      assertEquals("setURL", PropertyUtilBase.suggestSetterName("URL"));
      assertEquals("setURL", PropertyUtilBase.suggestSetterName(createField("URL", "Object")));

      assertEquals("setaURL", PropertyUtilBase.suggestSetterName("aURL"));
      assertEquals("setaURL", PropertyUtilBase.suggestSetterName(createField("aURL", "Object")));
      assertEquals("setBool", PropertyUtilBase.suggestSetterName(createField("bool", "java.lang.Boolean")));
    });
  }

  @Test
  public void testSuggestPropertyName() {
    assertEquals("valid", PropertyUtilBase.getPropertyName("isValid"));
    assertEquals("valid", PropertyUtilBase.getPropertyName("getValid"));
    assertEquals("valid", PropertyUtilBase.getPropertyName("setValid"));

    assertEquals("URL", PropertyUtilBase.getPropertyName("isURL"));
    assertEquals("URL", PropertyUtilBase.getPropertyName("getURL"));
    assertEquals("URL", PropertyUtilBase.getPropertyName("setURL"));

    assertEquals("aURL", PropertyUtilBase.getPropertyName("isaURL"));
    assertEquals("aURL", PropertyUtilBase.getPropertyName("getaURL"));
    assertEquals("aURL", PropertyUtilBase.getPropertyName("setaURL"));
  }

  @Test
  public void testBooleanPropertyGetters() {
    ReadAction.runBlocking(() -> {
      assertTrue(PropertyUtilBase.hasGetterName(createMethod("isOk", "boolean")));
      assertFalse(PropertyUtilBase.hasGetterName(createMethod("isOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
      assertTrue(PropertyUtilBase.hasGetterName(createMethod("getOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
      assertFalse(PropertyUtilBase.hasGetterName(createMethod("isOk", "int")));
    });
  }

  private static PsiType getType(@NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.getInstance(projectFixture.get().getProject()).createTypeFromText(type, null);
  }

  private static PsiField createField(@NonNls String name, @NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.getInstance(projectFixture.get().getProject()).createField(name, getType(type));
  }

  private static PsiMethod createMethod(@NonNls String name, String type) throws IncorrectOperationException {
    return PsiElementFactory.getInstance(projectFixture.get().getProject()).createMethod(name, getType(type));
  }
}
