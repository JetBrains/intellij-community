package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.testOnly.TestOnlyInspection;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class TestOnlyInspectionTest extends InspectionTestCase {
  @Override
  protected void setupRootModel(String testDir, VirtualFile[] sourceDir, String jdkName) {
    super.setupRootModel(testDir, sourceDir, jdkName);
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(testDir);
    VirtualFile test = projectDir.findChild("test");
    if (test != null) PsiTestUtil.addSourceRoot(myModule, test, true);
  }

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    return new AnalysisScope(myModule);
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testInsideInner() throws Exception {
    doTest();
  }

  public void testConstructor() throws Exception {
    doTest();
  }

  public void testVisibleForTesting() throws Exception { doTest(); }

  public void testUnresolved() throws Exception {
    doTest(); // shouldn't throw
  }

  private void doTest() throws Exception {
    TestOnlyInspection i = new TestOnlyInspection();
    doTest("testOnly/" + getTestName(true), new LocalInspectionToolWrapper(i), "java 1.5");
  }
}