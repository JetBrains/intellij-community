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

public class NullableStuffInspection14Test extends InspectionTestCase {
  private final NullableStuffInspection myInspection = new NullableStuffInspection();
  {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest14() throws Exception {
    myExcludeAnnotations = true;
    try {
      doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection),"java 1.4");
    }
    finally {
      myExcludeAnnotations = false;
    }
  }

  public void testJdk14() throws Exception{ doTest14(); }

  private boolean myExcludeAnnotations = false;

  @Override
  protected void setupRootModel(String testDir, VirtualFile[] sourceDir, String sdkName) {
    super.setupRootModel(testDir, sourceDir, sdkName);

    if (myExcludeAnnotations) {
      final Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
      assert sdk != null;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          final SdkModificator sdkMod = sdk.getSdkModificator();
          for (VirtualFile file : sdkMod.getRoots(OrderRootType.CLASSES)) {
            if ("annotations.jar".equals(file.getName())) {
              sdkMod.removeRoot(file, OrderRootType.CLASSES);
              break;
            }
          }
          sdkMod.commitChanges();
        }
      });
    }
  }
}