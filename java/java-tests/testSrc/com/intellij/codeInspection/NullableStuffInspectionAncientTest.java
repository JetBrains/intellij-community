/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.07.2006
 * Time: 0:07:45
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class NullableStuffInspectionAncientTest extends InspectionTestCase {
  private final NullableStuffInspection myInspection = new NullableStuffInspection();
  {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  public void testJdk14() throws Exception{
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection), "java 1.4");
  }

  public void testJdkAnnotationsWithoutJetBrainsAnnotations() throws Exception{
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection), "java 1.5");
  }

  @Override
  protected void setupRootModel(@NotNull String testDir, @NotNull VirtualFile[] sourceDir, String sdkName) {
    super.setupRootModel(testDir, sourceDir, sdkName);
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    removeAnnotationsJar(sdk);
    if ("testJdkAnnotationsWithoutJetBrainsAnnotations".equals(getName())) {
      PsiTestUtil.addJdkAnnotations(sdk);
    }
  }

  private static void removeAnnotationsJar(final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final SdkModificator sdkMod = sdk.getSdkModificator();
      for (VirtualFile file : sdkMod.getRoots(OrderRootType.CLASSES)) {
        if ("annotations.jar".equals(file.getName())) {
          sdkMod.removeRoot(file, OrderRootType.CLASSES);
          break;
        }
      }
      sdkMod.commitChanges();
    });
  }
}