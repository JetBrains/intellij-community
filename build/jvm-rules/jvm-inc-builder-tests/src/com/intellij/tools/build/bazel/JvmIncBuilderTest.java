package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.Test;


public class JvmIncBuilderTest extends BazelIncBuildTest {

  @Test
  public void testConvertJavaToKotlinGetterUsages() throws Exception {
    performTest("kotlin/convertJavaToKotlinGetterUsages").assertFailure();
  }

  @Test
  public void testInlineFunctionImplementationChanged() throws Exception {
    performTest("kotlin/inlineFunctionImplementationChanged").assertSuccessful();
  }

  @Test
  public void testAssignJavaFieldFromKotlinSubclass() throws Exception {
    performTest("kotlin/assignJavaFieldFromKotlinSubclass").assertFailure();
  }

  @Test
  public void testAssignFieldFromSubclassAcrossTargets() throws Exception {
    performTest("java/assignFieldFromSubclassAcrossTargets").assertFailure();
  }

  @Test
  public void testRebuildOnUntrackedInputChange() throws Exception {
    performTest("worker/rebuildOnUntrackedInputChange").assertSuccessful();
  }
}
