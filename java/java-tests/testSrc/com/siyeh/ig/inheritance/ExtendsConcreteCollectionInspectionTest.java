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
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ExtendsConcreteCollectionInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ExtendsConcreteCollectionInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
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

  public void testExtendsConcreteCollection() {
    doTest();
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
