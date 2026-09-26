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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;

/**
 * @author Bas Leijdekkers
 */
public class InstanceMethodNamingConventionInspectionTest extends AbstractMethodNamingConventionInspectionTest {

  @Override
  protected InspectionProfileEntry getInspection() {
    NewMethodNamingConventionInspection inspection = new NewMethodNamingConventionInspection();
    inspection.setEnabled(true, new InstanceMethodNamingConvention().getShortName());
    String nativeShortName = new NativeMethodNamingConvention().getShortName();
    inspection.setEnabled(true, nativeShortName);
    inspection.getNamingConventionBean(nativeShortName).m_minLength = 0;
    return inspection;
  }

  public void testInstanceMethodNamingConvention() { doTest(); }
}
