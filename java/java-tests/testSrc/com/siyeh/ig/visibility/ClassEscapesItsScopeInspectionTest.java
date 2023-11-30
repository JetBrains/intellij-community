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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ClassEscapesItsScopeInspectionTest extends LightJavaInspectionTestCase {
  private ClassEscapesItsScopeInspection myInspection = new ClassEscapesItsScopeInspection();

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  public void testClassEscapesItsScope() { doTest(true, true); }

  public void testGenericParameterEscapesItsScope() { doTest(true, true); }

  public void testExposedByPublic() {
    doTest(true, false);
  }

  public void testExposedByPackageLocal() {
    doTest(false, true);
  }

  private void doTest(boolean checkPublicApi, boolean checkPackageLocal) {
    myInspection.checkPublicApi = checkPublicApi;
    myInspection.checkPackageLocal = checkPackageLocal;
    try {
      doTest();
    }
    finally {
      myInspection.checkPublicApi = myInspection.checkPackageLocal = false;
    }
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return myInspection;
  }
}