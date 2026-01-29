package com.intellij.tools.build.bazel;

import com.google.devtools.build.runfiles.Runfiles;
import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import static org.junit.Assert.fail;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  JvmIncBuilderTest.class,
  ZipBuilderTest.class,
  DependencyGraphTest.class,
  KotlinCriTest.class
})
public class AllTests {

  @BeforeClass
  public static void expandPaths() throws Exception {
    Runfiles.Preloaded preloaded = Runfiles.preload();
    adjustPath(BazelIncBuildTest.BAZEL_EXECUTABLE, preloaded);
    adjustPath(BazelIncBuildTest.BAZEL_TEST_WORKSPACE_FILE, preloaded);
    adjustPath(BazelIncBuildTest.RULES_JVM_SNAPSHOT_FILE, preloaded);
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