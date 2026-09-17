package com.intellij.tools.build.bazel;

import com.google.devtools.build.runfiles.Runfiles;
import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation.ThreadingModelInstrumenterTest;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2Test;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter1Test;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter2Test;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenterTest;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import static org.junit.Assert.fail;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  JvmIncBuilderTest.class,
  CrashRecoveryTest.class,
  KotlinTests.class,
  JavaTests.class,
  ZipBuilderTest.class,
  IteratorsTest.class,
  DependencyGraphTest.class,
  JavacProtoUtilTest.class,
  KotlinCriTest.class,
  JavaAbiFilterTest.class,
  JavaAnnotationProcessorTests.class,
  WarningLevelTests.class,
  BuildContextKotlinOptionsTest.class,
  BuildContextJavaOptionsTest.class,
  TMHInstrumenter1Test.class,
  TMHInstrumenter2Test.class,
  ThreadingModelInstrumenterTest.class,
  TMHAssertionGenerator2Test.class,
  TMHInstrumenterTest.class
})
public class AllTests {

  @BeforeClass
  public static void expandPaths() throws Exception {
    Runfiles.Preloaded preloaded = Runfiles.preload();
    adjustPath(BazelIncBuildTest.BAZEL_EXECUTABLE, preloaded);
    adjustPath(BazelIncBuildTest.BAZEL_TEST_WORKSPACE_FILE, preloaded);
    adjustPath(BazelIncBuildTest.RULES_JVM_SNAPSHOT_FILE, preloaded);
    adjustPath("jvm-inc-builder.tmh.test-data", preloaded);
  }

  private static void adjustPath(String pathProperty, Runfiles.Preloaded runfiles) {
    String relativePath = System.getProperty(pathProperty);
    if (relativePath != null) {
      String absPath = runfiles.unmapped().rlocation(relativePath);
      System.setProperty(pathProperty, absPath);
      System.err.println("-D" + pathProperty + "=" + absPath);
    }
    else {
      fail(pathProperty + " property is not set");
    }
  }
}