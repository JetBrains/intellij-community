/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class LocalMissingPackageInfoInspectionTest extends LightJavaInspectionTestCase {

  public void testLocalInspection() {
    addEnvironmentClass("package a;" +
                        "class Y {}");
    doTest("package /*Package 'a' is missing a 'package-info.java' file*/a/**/;" +
           "class X {}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MissingPackageInfoInspection();
  }
}
