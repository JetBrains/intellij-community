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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.naming.AbstractMethodNamingConventionInspectionTest;
import com.siyeh.ig.naming.NewMethodNamingConventionInspection;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class JUnit3MethodNamingConventionInspectionTest extends AbstractMethodNamingConventionInspectionTest {

  public void testJUnit3MethodNamingConvention() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    NewMethodNamingConventionInspection inspection = new NewMethodNamingConventionInspection();
    inspection.setEnabled(true, new JUnit3MethodNamingConvention().getShortName());
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework;" +
      "public abstract class TestCase {" +
      "    protected void setUp() throws Exception {}" +
      "    protected void tearDown() throws Exception {}" +
      "}"
    };
  }
}
