package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.impl.BazelTestProgressExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BazelTestProgressExtension.class)
public class JvmIncBuilderTest extends BazelIncBuildTest {

  @Test
  void testConvertJavaToKotlinGetterUsages() throws Exception {
    performTest("kotlin/convertJavaToKotlinGetterUsages").assertFailure();
  }
}
