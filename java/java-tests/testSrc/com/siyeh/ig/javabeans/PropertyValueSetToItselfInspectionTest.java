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
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PropertyValueSetToItselfInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
             class Bean {
               private String x;
               public void setX(String x) {
                 this.x = x;
               }
               public String getX() { return x; }
               void m(Bean b) {
                 (b)./*Property value set to itself*/setX/**/(b.getX());
                 this./*Property value set to itself*/setX/**/(getX());
               }
             }""");
  }

  public void testNoWarn() {
    doTest("""
             class Bean {
               private String x;
               public void setX(String x) {
                 this.x = x;
               }
               public String getX() { return x; }
               void m(Bean b, Bean c) {
                 (b).setX(c.getX());
               }
             }""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PropertyValueSetToItselfInspection();
  }
}