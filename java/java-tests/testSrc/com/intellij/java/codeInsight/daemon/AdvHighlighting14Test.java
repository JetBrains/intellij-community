// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
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
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_4;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }

  public void testPackageLocals() throws Exception {
    doTest(BASE_PATH + "/packageLocals", BASE_PATH + "/packageLocals/x/sub/UsingMain.java");
  }

  public void testPackageLocalClassInTheMiddle() throws Exception {
    doTest(BASE_PATH + "/packageLocals", BASE_PATH + "/packageLocals/x/A.java");
  }

  public void testEffectiveAccessLevel() throws Exception {
    doTest(BASE_PATH + "/accessLevel", BASE_PATH + "/accessLevel/effectiveAccess/p2/p3.java");
  }

  public void testSingleImportConflict() throws Exception {
    doTest(BASE_PATH + "/singleImport", BASE_PATH + "/singleImport/d.java");
  }

  public void testDuplicateTopLevelClass() throws Exception {
    doTest(BASE_PATH + "/duplicateClass", BASE_PATH + "/duplicateClass/A.java");
  }

  public void testDuplicateTopLevelClass2() throws Exception {
    doTest(BASE_PATH + "/duplicateClass", BASE_PATH + "/duplicateClass/java/lang/Runnable.java");
  }

  public void testProtectedConstructorCall() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor", BASE_PATH + "/protectedConstructor/p2/C2.java");
  }

  public void testProtectedConstructorCallInSamePackage() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor", BASE_PATH + "/protectedConstructor/samePackage/C2.java");
  }

  public void testProtectedConstructorCallInInner() throws Exception {
    doTest(BASE_PATH + "/protectedConstructorInInner", BASE_PATH + "/protectedConstructorInInner/p2/C2.java");
  }

  public void testArrayLengthAccessFromSubClass() throws Exception {
    doTest(BASE_PATH + "/arrayLength", BASE_PATH + "/arrayLength/p2/SubTest.java");
  }

  public void testAccessibleMember() throws Exception {
    doTest(BASE_PATH + "/accessibleMember", BASE_PATH + "/accessibleMember/com/red/C.java");
  }

  public void testStaticPackageLocalMember() throws Exception {
    doTest(BASE_PATH + "/staticPackageLocalMember", BASE_PATH + "/staticPackageLocalMember/p1/C.java");
  }

  public void testOnDemandImportConflict() throws Exception {
    doTest(BASE_PATH + "/onDemandImportConflict", BASE_PATH + "/onDemandImportConflict/Outer.java");
  }

  public void testPackageLocalOverride() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride", BASE_PATH + "/packageLocalOverride/y/C.java");
  }

  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride", BASE_PATH + "/packageLocalOverride/y/B.java");
  }

  public void testProtectedAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage", BASE_PATH + "/protectedAccessFromOtherPackage/a/Main.java");
  }

  public void testProtectedFieldAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage", BASE_PATH + "/protectedAccessFromOtherPackage/a/A.java");
  }

  public void testPackageLocalClassInTheMiddle1() throws Exception {
    doTest(BASE_PATH + "/foreignPackageInBetween", BASE_PATH + "/foreignPackageInBetween/a/A1.java");
  }

  public void testImportOnDemand() throws Exception {
    doTest(BASE_PATH + "/importOnDemand", BASE_PATH + "/importOnDemand/y/Y.java");
  }

  public void testImportOnDemandVsSingle() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsSingle", BASE_PATH + "/importOnDemandVsSingle/y/Y.java");
  }

  public void testImportSingleVsSamePackage() throws Exception {
    doTest(BASE_PATH + "/importSingleVsSamePackage", BASE_PATH + "/importSingleVsSamePackage/y/Y.java");
  }

  public void testImportSingleVsInherited() throws Exception {
    doTest(BASE_PATH + "/importSingleVsInherited", BASE_PATH + "/importSingleVsInherited/Test.java");
  }

  public void testImportOnDemandVsInherited() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsInherited", BASE_PATH + "/importOnDemandVsInherited/Test.java");
  }

  public void testOverridePackageLocal() throws Exception {
    doTest(BASE_PATH + "/overridePackageLocal", BASE_PATH + "/overridePackageLocal/x/y/Derived.java");
  }

  public void testAlreadyImportedClass() throws Exception {
    doTest(BASE_PATH + "/alreadyImportedClass", BASE_PATH + "/alreadyImportedClass/pack/AlreadyImportedClass.java");
  }

  public void testNotAKeywords() throws Exception {
    doTest(BASE_PATH + "/notAKeywords", BASE_PATH + "/notAKeywords/Test.java");
  }

  public void testPackageAndClassConflict1() throws Exception {
    doTest(BASE_PATH + "/packageClassClash1", BASE_PATH + "/packageClassClash1/pkg/sub/Test.java", BASE_PATH + "/packageClassClash1/pkg/sub.java");
  }

  public void testPackageAndClassConflict21() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2", BASE_PATH + "/packageClassClash2/pkg/sub/Test.java");
  }

  public void testPackageAndClassConflict22() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2", BASE_PATH + "/packageClassClash2/pkg/Sub.java");
  }

  public void testPackageAndClassConflictNoClassInSubdir() throws Exception {
    doTest(BASE_PATH + "/packageClassClashNoClassInDir", BASE_PATH + "/packageClassClashNoClassInDir/pkg/sub.java");
  }

  public void testDefaultPackageAndClassConflict() {
    myFixture.configureByFile(BASE_PATH + "/lang.java");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testPackageObscuring() throws Exception {
    doTest(BASE_PATH + "/packageObscuring", BASE_PATH + "/packageObscuring/main/Main.java");
  }
  public void testPublicClassInRightFile() throws Exception {
    doTest(BASE_PATH + "/publicClassInRightFile", BASE_PATH + "/publicClassInRightFile/x/X.java");
  }
  public void testPublicClassInRightFile2() throws Exception {
    doTest(BASE_PATH + "/publicClassInRightFile", BASE_PATH + "/publicClassInRightFile/x/Y.java");
  }

  private void doTest(String dir, String @NotNull ... filePaths) throws Exception {
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

    myFixture.configureByFiles(filePaths);
    myFixture.checkHighlighting(false, false, false);
  }
}
