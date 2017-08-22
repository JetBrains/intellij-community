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

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class PropertyUtilTest extends LightCodeInsightTestCase {
  public void testSuggestGetterName() {
    assertEquals("isValid", PropertyUtil.suggestGetterName("valid", getType("boolean")));
    assertEquals("getValid", PropertyUtil.suggestGetterName("valid", getType("Object")));
    assertEquals("isValid", PropertyUtil.suggestGetterName(createField("valid", "boolean")));
    assertEquals("getValid", PropertyUtil.suggestGetterName(createField("valid", "Object")));

    assertEquals("getURL", PropertyUtil.suggestGetterName("URL", getType("Object")));
    assertEquals("getURL", PropertyUtil.suggestGetterName(createField("URL", "Object")));

    assertEquals("isaURL", PropertyUtil.suggestGetterName(createField("aURL", "boolean")));
    assertEquals("getaURL", PropertyUtil.suggestGetterName(createField("aURL", "Object")));
    assertEquals("getBool", PropertyUtil.suggestGetterName(createField("bool", "java.lang.Boolean")));
  }

  public void testSuggestSetterName() {
    assertEquals("setValid", PropertyUtil.suggestSetterName("valid"));
    assertEquals("setValid", PropertyUtil.suggestSetterName(createField("valid", "Object")));

    assertEquals("setURL", PropertyUtil.suggestSetterName("URL"));
    assertEquals("setURL", PropertyUtil.suggestSetterName(createField("URL", "Object")));

    assertEquals("setaURL", PropertyUtil.suggestSetterName("aURL"));
    assertEquals("setaURL", PropertyUtil.suggestSetterName(createField("aURL", "Object")));
    assertEquals("setBool", PropertyUtil.suggestSetterName(createField("bool", "java.lang.Boolean")));
  }

  public void testSuggestPropertyName() {
    assertEquals("valid", PropertyUtil.getPropertyName("isValid"));
    assertEquals("valid", PropertyUtil.getPropertyName("getValid"));
    assertEquals("valid", PropertyUtil.getPropertyName("setValid"));

    assertEquals("URL", PropertyUtil.getPropertyName("isURL"));
    assertEquals("URL", PropertyUtil.getPropertyName("getURL"));
    assertEquals("URL", PropertyUtil.getPropertyName("setURL"));

    assertEquals("aURL", PropertyUtil.getPropertyName("isaURL"));
    assertEquals("aURL", PropertyUtil.getPropertyName("getaURL"));
    assertEquals("aURL", PropertyUtil.getPropertyName("setaURL"));
  }

  public void testBooleanPropertyGetters() {
    assertTrue(PropertyUtil.hasGetterName(createMethod("isOk", "boolean")));
    assertFalse(PropertyUtil.hasGetterName(createMethod("isOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
    assertTrue(PropertyUtil.hasGetterName(createMethod("getOk", CommonClassNames.JAVA_LANG_BOOLEAN)));
    assertFalse(PropertyUtil.hasGetterName(createMethod("isOk", "int")));
  }

  private static PsiType getType(@NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createTypeFromText(type, null);
  }

  private static PsiField createField(@NonNls String name, @NonNls String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createField(name, getType(type));
  }

  private static PsiMethod createMethod(@NonNls String name, String type) throws IncorrectOperationException {
    return PsiElementFactory.SERVICE.getInstance(ourProject).createMethod(name, getType(type));
  }
}
