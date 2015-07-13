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
  protected void setupRootModel(String testDir, VirtualFile[] sourceDir, String sdkName) {
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