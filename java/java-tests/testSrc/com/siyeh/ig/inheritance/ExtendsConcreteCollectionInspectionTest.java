/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ExtendsConcreteCollectionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ExtendsConcreteCollectionInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[]{"""
      package java.util;
      public class LinkedHashMap<K, V> extends HashMap<K,V> implements Map<K,V>{
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
          return false;
        }
      }""",
      """
      package java.util;
      public class ArrayDeque<E> extends AbstractCollection<E> {
        @Override public Iterator<E> iterator() { return Collections.emptyIterator(); }
        @Override public int size() { return 0; }
      }"""
    };
  }

  @Test
  public void testExtendsConcreteCollection() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    String message = InspectionGadgetsBundle.message("replace.inheritance.with.delegation.quickfix");
    final IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   package com.siyeh.igtest.inheritance.extends_concrete_collection;
                                      
                   import java.util.ArrayDeque;
                   import java.util.ArrayList;
                   import java.util.LinkedHashMap;
                   import java.util.Map;
                                      
                   class ExtendsConcreteCollection extends ArrayList {
                                      
                   }
                   class MyMap extends LinkedHashMap<String, String> {
                     @Override
                     protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                       return true;
                     }
                   }
                   class MyDeque {
                       private final ArrayDeque arrayDeque = new ArrayDeque();
                   }""", text);
  }
}
