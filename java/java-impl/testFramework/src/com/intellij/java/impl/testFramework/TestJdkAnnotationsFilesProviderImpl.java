package com.intellij.java.impl.testFramework;

import com.intellij.openapi.projectRoots.testFramework.TestJdkAnnotationsFilesProvider;
import com.intellij.testFramework.common.BazelTestUtil;

import java.nio.file.Path;

public class TestJdkAnnotationsFilesProviderImpl implements TestJdkAnnotationsFilesProvider {
  @Override
  public Path getJdkAnnotationsPath() {
    Path path = BazelTestUtil.findRunfilesDirectoryUnderCommunityOrUltimate("java/jdkAnnotations/resources");
    BazelTestUtil.allowVfsAccessToCanonicalRunfilesRoot(path);
    return path;
  }
}
