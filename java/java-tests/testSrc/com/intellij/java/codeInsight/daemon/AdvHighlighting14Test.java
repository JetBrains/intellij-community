// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

public class AdvHighlighting14Test extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_4;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting";
  }

  public void testPackageLocals() {
    doTest("/packageLocals", "/packageLocals/x/sub/UsingMain.java");
  }

  public void testPackageLocalClassInTheMiddle() {
    doTest("/packageLocals",  "/packageLocals/x/A.java");
  }

  public void testEffectiveAccessLevel() {
    doTest("/accessLevel", "/accessLevel/effectiveAccess/p2/p3.java");
  }

  public void testSingleImportConflict() {
    doTest("/singleImport", "/singleImport/d.java");
  }

  public void testDuplicateTopLevelClass() {
    doTest("/duplicateClass", "/duplicateClass/A.java");
  }

  public void testDuplicateTopLevelClass2() {
    doTest("/duplicateClass", "/duplicateClass/java/lang/Runnable.java");
  }

  public void testProtectedConstructorCall() {
    doTest("/protectedConstructor", "/protectedConstructor/p2/C2.java");
  }

  public void testProtectedConstructorCallInSamePackage() {
    doTest("/protectedConstructor", "/protectedConstructor/samePackage/C2.java");
  }

  public void testProtectedConstructorCallInInner() {
    doTest("/protectedConstructorInInner", "/protectedConstructorInInner/p2/C2.java");
  }

  public void testArrayLengthAccessFromSubClass() {
    doTest("/arrayLength", "/arrayLength/p2/SubTest.java");
  }

  public void testAccessibleMember() {
    doTest("/accessibleMember", "/accessibleMember/com/red/C.java");
  }

  public void testStaticPackageLocalMember() {
    doTest("/staticPackageLocalMember", "/staticPackageLocalMember/p1/C.java");
  }

  public void testOnDemandImportConflict() {
    doTest("/onDemandImportConflict", "/onDemandImportConflict/Outer.java");
  }

  public void testPackageLocalOverride() {
    doTest("/packageLocalOverride", "/packageLocalOverride/y/C.java");
  }

  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() {
    doTest("/packageLocalOverride", "/packageLocalOverride/y/B.java");
  }

  public void testProtectedAccessFromOtherPackage() {
    doTest("/protectedAccessFromOtherPackage", "/protectedAccessFromOtherPackage/a/Main.java");
  }

  public void testProtectedFieldAccessFromOtherPackage() {
    doTest("/protectedAccessFromOtherPackage", "/protectedAccessFromOtherPackage/a/A.java");
  }

  public void testPackageLocalClassInTheMiddle1() {
    doTest("/foreignPackageInBetween", "/foreignPackageInBetween/a/A1.java");
  }

  public void testImportOnDemand() {
    doTest("/importOnDemand", "/importOnDemand/y/Y.java");
  }

  public void testImportOnDemandVsSingle() {
    doTest("/importOnDemandVsSingle", "/importOnDemandVsSingle/y/Y.java");
  }

  public void testImportSingleVsSamePackage() {
    doTest("/importSingleVsSamePackage", "/importSingleVsSamePackage/y/Y.java");
  }

  public void testImportSingleVsInherited() {
    doTest("/importSingleVsInherited", "/importSingleVsInherited/Test.java");
  }

  public void testImportOnDemandVsInherited() {
    doTest("/importOnDemandVsInherited", "/importOnDemandVsInherited/Test.java");
  }

  public void testOverridePackageLocal() {
    doTest("/overridePackageLocal", "/overridePackageLocal/x/y/Derived.java");
  }

  public void testAlreadyImportedClass() {
    doTest("/alreadyImportedClass", "/alreadyImportedClass/pack/AlreadyImportedClass.java");
  }

  public void testNotAKeywords() {
    doTest("/notAKeywords", "/notAKeywords/Test.java");
  }

  public void testPackageAndClassConflict1() {
    doTest("/packageClassClash1", "/packageClassClash1/pkg/sub/Test.java", "/packageClassClash1/pkg/sub.java");
  }

  public void testPackageAndClassConflict21() {
    doTest("/packageClassClash2", "/packageClassClash2/pkg/sub/Test.java");
  }

  public void testPackageAndClassConflict22() {
    doTest("/packageClassClash2", "/packageClassClash2/pkg/Sub.java");
  }

  public void testPackageAndClassConflictNoClassInSubdir() {
    doTest("/packageClassClashNoClassInDir", "/packageClassClashNoClassInDir/pkg/sub.java");
  }

  public void testDefaultPackageAndClassConflict() {
    myFixture.configureByFile("/lang.java");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testPackageObscuring() {
    doTest("/packageObscuring", "/packageObscuring/main/Main.java");
  }

  public void testPublicClassInRightFile() {
    doTest("/publicClassInRightFile", "/publicClassInRightFile/x/X.java");
  }

  public void testPublicClassInRightFile2() {
    doTest("/publicClassInRightFile", "/publicClassInRightFile/x/Y.java");
  }

  private void doTest(String dir, String @NotNull ... filePaths) {
    try {
      File basePath = new File(getTestDataPath());
      File currentDir = new File(basePath, dir);
      Files.walkFileTree(currentDir.toPath(), new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          File additionalFile = file.toFile();
          String additionalPath = "/" + FileUtil.toSystemIndependentName(FileUtil.getRelativePath(basePath, additionalFile));
          if (!Arrays.asList(filePaths).contains(additionalPath)) {
            myFixture.addFileToProject("/" + FileUtil.toSystemIndependentName(FileUtil.getRelativePath(currentDir, additionalFile)),
                                       FileUtil.loadFile(additionalFile));
          }
          return super.visitFile(file, attrs);
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    myFixture.configureByFiles(filePaths);
    myFixture.checkHighlighting(false, false, false);
  }
}
