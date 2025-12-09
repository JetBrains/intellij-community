package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.impl.BazelTestProgressExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BazelTestProgressExtension.class)
public class JvmIncBuilderTest extends BazelIncBuildTest {

  @Test
  void testConvertJavaToKotlinGetterUsages() throws Exception {
    performTest("kotlin/convertJavaToKotlinGetterUsages").assertFailure();
  }

  @Test
  void testInlineFunctionImplementationChanged() throws Exception {
    performTest("kotlin/inlineFunctionImplementationChanged").assertSuccessful();
  }

  @Disabled("Until kotlinc 2.3 is used for compilation")
  @Test
  void testAssignJavaFieldFromKotlinSubclass() throws Exception {
    performTest("kotlin/assignJavaFieldFromKotlinSubclass").assertFailure();
  }
}
