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
package com.intellij.java.psi.codeStyle;

import com.intellij.ide.codeStyleSettings.CodeStyleTestCase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class JavaCodeStyleSettingsTest extends CodeStyleTestCase {

  public void testSettingsClone() {
    List<String> annotations = Arrays.asList("anno1", "anno2");
    JavaCodeStyleSettings original = (JavaCodeStyleSettings)JavaCodeStyleSettings.getInstance(getProject()).clone();
    original.getImportLayoutTable().addEntry(new PackageEntry(false, "test", true));
    original.setRepeatAnnotations(annotations);
    original.getPackagesToUseImportOnDemand().addEntry(new PackageEntry(false, "test2", true));
    original.FIELD_TYPE_TO_NAME.addPair("foo", "bar");
    original.STATIC_FIELD_TYPE_TO_NAME.addPair("one", "two");

    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    assertEquals(annotations, copy.getRepeatAnnotations());
    assertEquals("Import tables do not match", original.getImportLayoutTable(), copy.getImportLayoutTable());
    assertEquals("On demand packages do not match", original.getPackagesToUseImportOnDemand(), copy.getPackagesToUseImportOnDemand());
    assertEquals("Field type-to-name maps don not match", original.FIELD_TYPE_TO_NAME, copy.FIELD_TYPE_TO_NAME);
    assertEquals("Static field type-to-name maps don not match", original.STATIC_FIELD_TYPE_TO_NAME, copy.STATIC_FIELD_TYPE_TO_NAME);
  }

  public void testSettingsCloneNotReferencingOriginal() throws IllegalAccessException {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    for (Field field : copy.getClass().getDeclaredFields()) {
      if (!isPrimitiveOrString(field.getType()) && (field.getModifiers() & Modifier.PUBLIC) != 0) {
        assertNotSame("Fields '" + field.getName() + "' reference the same value", field.get(original), field.get(copy));
      }
    }
  }

  private static boolean isPrimitiveOrString(Class type) {
    return type.isPrimitive() || type.equals(String.class);
  }
}
