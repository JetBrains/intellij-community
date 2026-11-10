// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AdvHighlighting14Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_4);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting");
  }

  @Test
  public void testPackageLocals() {
    doTest("/packageLocals", "/packageLocals/x/sub/UsingMain.java");
  }

  @Test
  public void testPackageLocalClassInTheMiddle() {
    doTest("/packageLocals",  "/packageLocals/x/A.java");
  }

  @Test
  public void testEffectiveAccessLevel() {
    doTest("/accessLevel", "/accessLevel/effectiveAccess/p2/p3.java");
  }

  @Test
  public void testSingleImportConflict() {
    doTest("/singleImport", "/singleImport/d.java");
  }

  @Test
  public void testDuplicateTopLevelClass() {
    doTest("/duplicateClass", "/duplicateClass/A.java");
  }

  @Test
  public void testDuplicateTopLevelClass2() {
    doTest("/duplicateClass", "/duplicateClass/java/lang/Runnable.java");
  }

  @Test
  public void testProtectedConstructorCall() {
    doTest("/protectedConstructor", "/protectedConstructor/p2/C2.java");
  }

  @Test
  public void testProtectedConstructorCallInSamePackage() {
    doTest("/protectedConstructor", "/protectedConstructor/samePackage/C2.java");
  }

  @Test
  public void testProtectedConstructorCallInInner() {
    doTest("/protectedConstructorInInner", "/protectedConstructorInInner/p2/C2.java");
  }

  @Test
  public void testArrayLengthAccessFromSubClass() {
    doTest("/arrayLength", "/arrayLength/p2/SubTest.java");
  }

  @Test
  public void testAccessibleMember() {
    doTest("/accessibleMember", "/accessibleMember/com/red/C.java");
  }

  @Test
  public void testStaticPackageLocalMember() {
    doTest("/staticPackageLocalMember", "/staticPackageLocalMember/p1/C.java");
  }

  @Test
  public void testOnDemandImportConflict() {
    doTest("/onDemandImportConflict", "/onDemandImportConflict/Outer.java");
  }

  @Test
  public void testPackageLocalOverride() {
    doTest("/packageLocalOverride", "/packageLocalOverride/y/C.java");
  }

  @Test
  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() {
    doTest("/packageLocalOverride", "/packageLocalOverride/y/B.java");
  }

  @Test
  public void testProtectedAccessFromOtherPackage() {
    doTest("/protectedAccessFromOtherPackage", "/protectedAccessFromOtherPackage/a/Main.java");
  }

  @Test
  public void testProtectedFieldAccessFromOtherPackage() {
    doTest("/protectedAccessFromOtherPackage", "/protectedAccessFromOtherPackage/a/A.java");
  }

  @Test
  public void testPackageLocalClassInTheMiddle1() {
    doTest("/foreignPackageInBetween", "/foreignPackageInBetween/a/A1.java");
  }

  @Test
  public void testImportOnDemand() {
    doTest("/importOnDemand", "/importOnDemand/y/Y.java");
  }

  @Test
  public void testImportOnDemandVsSingle() {
    doTest("/importOnDemandVsSingle", "/importOnDemandVsSingle/y/Y.java");
  }

  @Test
  public void testImportSingleVsSamePackage() {
    doTest("/importSingleVsSamePackage", "/importSingleVsSamePackage/y/Y.java");
  }

  @Test
  public void testImportSingleVsInherited() {
    doTest("/importSingleVsInherited", "/importSingleVsInherited/Test.java");
  }

  @Test
  public void testImportOnDemandVsInherited() {
    doTest("/importOnDemandVsInherited", "/importOnDemandVsInherited/Test.java");
  }

  @Test
  public void testOverridePackageLocal() {
    doTest("/overridePackageLocal", "/overridePackageLocal/x/y/Derived.java");
  }

  @Test
  public void testAlreadyImportedClass() {
    doTest("/alreadyImportedClass", "/alreadyImportedClass/pack/AlreadyImportedClass.java");
  }

  @Test
  public void testNotAKeywords() {
    doTest("/notAKeywords", "/notAKeywords/Test.java");
  }

  @Test
  public void testPackageAndClassConflict1() {
    doTest("/packageClassClash1", "/packageClassClash1/pkg/sub/Test.java", "/packageClassClash1/pkg/sub.java");
  }

  @Test
  public void testPackageAndClassConflict21() {
    doTest("/packageClassClash2", "/packageClassClash2/pkg/sub/Test.java");
  }

  @Test
  public void testPackageAndClassConflict22() {
    doTest("/packageClassClash2", "/packageClassClash2/pkg/Sub.java");
  }

  @Test
  public void testPackageAndClassConflictNoClassInSubdir() {
    doTest("/packageClassClashNoClassInDir", "/packageClassClashNoClassInDir/pkg/sub.java");
  }

  @Test
  public void testDefaultPackageAndClassConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/lang.java");
    myFixture.checkHighlighting(false, false, false);
  }

  @Test
  public void testPackageObscuring() {
    doTest("/packageObscuring", "/packageObscuring/main/Main.java");
  }

  @Test
  public void testPublicClassInRightFile() {
    doTest("/publicClassInRightFile", "/publicClassInRightFile/x/X.java");
  }

  @Test
  public void testPublicClassInRightFile2() {
    doTest("/publicClassInRightFile", "/publicClassInRightFile/x/Y.java");
  }

  private void doTest(String dir, String @NotNull ... filePaths) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      File basePath = new File(myFixture.getTestDataPath());
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
