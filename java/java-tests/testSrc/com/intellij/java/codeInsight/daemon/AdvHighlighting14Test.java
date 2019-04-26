// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class AdvHighlighting14Test extends LightCodeInsightFixtureTestCase {
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
    doTest(BASE_PATH + "/packageLocals/x/sub/UsingMain.java", BASE_PATH + "/packageLocals");
  }

  public void testPackageLocalClassInTheMiddle() throws Exception {
    doTest(BASE_PATH + "/packageLocals/x/A.java", BASE_PATH + "/packageLocals");
  }

  public void testEffectiveAccessLevel() throws Exception {
    doTest(BASE_PATH + "/accessLevel/effectiveAccess/p2/p3.java", BASE_PATH + "/accessLevel");
  }

  public void testSingleImportConflict() throws Exception {
    doTest(BASE_PATH + "/singleImport/d.java", BASE_PATH + "/singleImport");
  }

  public void testDuplicateTopLevelClass() throws Exception {
    doTest(BASE_PATH + "/duplicateClass/A.java", BASE_PATH + "/duplicateClass");
  }

  public void testDuplicateTopLevelClass2() throws Exception {
    doTest(BASE_PATH + "/duplicateClass/java/lang/Runnable.java", BASE_PATH + "/duplicateClass");
  }

  public void testProtectedConstructorCall() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor/p2/C2.java", BASE_PATH + "/protectedConstructor");
  }

  public void testProtectedConstructorCallInSamePackage() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor/samePackage/C2.java", BASE_PATH + "/protectedConstructor");
  }

  public void testProtectedConstructorCallInInner() throws Exception {
    doTest(BASE_PATH + "/protectedConstructorInInner/p2/C2.java", BASE_PATH + "/protectedConstructorInInner");
  }

  public void testArrayLengthAccessFromSubClass() throws Exception {
    doTest(BASE_PATH + "/arrayLength/p2/SubTest.java", BASE_PATH + "/arrayLength");
  }

  public void testAccessibleMember() throws Exception {
    doTest(BASE_PATH + "/accessibleMember/com/red/C.java", BASE_PATH + "/accessibleMember");
  }

  public void testStaticPackageLocalMember() throws Exception {
    doTest(BASE_PATH + "/staticPackageLocalMember/p1/C.java", BASE_PATH + "/staticPackageLocalMember");
  }

  public void testOnDemandImportConflict() throws Exception {
    doTest(BASE_PATH + "/onDemandImportConflict/Outer.java", BASE_PATH + "/onDemandImportConflict");
  }

  public void testPackageLocalOverride() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride/y/C.java", BASE_PATH + "/packageLocalOverride");
  }

  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride/y/B.java", BASE_PATH + "/packageLocalOverride");
  }

  public void testProtectedAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage/a/Main.java", BASE_PATH + "/protectedAccessFromOtherPackage");
  }

  public void testProtectedFieldAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage/a/A.java", BASE_PATH + "/protectedAccessFromOtherPackage");
  }

  public void testPackageLocalClassInTheMiddle1() throws Exception {
    doTest(BASE_PATH + "/foreignPackageInBetween/a/A1.java", BASE_PATH + "/foreignPackageInBetween");
  }

  public void testImportOnDemand() throws Exception {
    doTest(BASE_PATH + "/importOnDemand/y/Y.java", BASE_PATH + "/importOnDemand");
  }

  public void testImportOnDemandVsSingle() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsSingle/y/Y.java", BASE_PATH + "/importOnDemandVsSingle");
  }

  public void testImportSingleVsSamePackage() throws Exception {
    doTest(BASE_PATH + "/importSingleVsSamePackage/y/Y.java", BASE_PATH + "/importSingleVsSamePackage");
  }

  public void testImportSingleVsInherited() throws Exception {
    doTest(BASE_PATH + "/importSingleVsInherited/Test.java", BASE_PATH + "/importSingleVsInherited");
  }

  public void testImportOnDemandVsInherited() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsInherited/Test.java", BASE_PATH + "/importOnDemandVsInherited");
  }

  public void testOverridePackageLocal() throws Exception {
    doTest(BASE_PATH + "/overridePackageLocal/x/y/Derived.java", BASE_PATH + "/overridePackageLocal");
  }

  public void testAlreadyImportedClass() throws Exception {
    doTest(BASE_PATH + "/alreadyImportedClass/pack/AlreadyImportedClass.java", BASE_PATH + "/alreadyImportedClass");
  }

  public void testNotAKeywords() throws Exception {
    doTest(BASE_PATH + "/notAKeywords/Test.java", BASE_PATH + "/notAKeywords");
  }

  public void testPackageAndClassConflict11() throws Exception {
    doTest(BASE_PATH + "/packageClassClash1/pkg/sub/Test.java", BASE_PATH + "/packageClassClash1");
  }
  
  public void testPackageAndClassConflict21() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2/pkg/sub/Test.java", BASE_PATH + "/packageClassClash2");
  }

  public void testPackageAndClassConflict22() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2/pkg/Sub.java", BASE_PATH + "/packageClassClash2");
  }

  public void testPackageAndClassConflictNoClassInSubdir() throws Exception {
    doTest(BASE_PATH + "/packageClassClashNoClassInDir/pkg/sub.java", BASE_PATH + "/packageClassClashNoClassInDir");
  }

  public void testDefaultPackageAndClassConflict() throws Exception {
    myFixture.configureByFile(BASE_PATH + "/lang.java");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testPackageObscuring() throws Exception {
    doTest(BASE_PATH + "/packageObscuring/main/Main.java", BASE_PATH + "/packageObscuring");
  }
  public void testPublicClassInRightFile() throws Exception {
    doTest(BASE_PATH + "/publicClassInRightFile/x/X.java", BASE_PATH + "/publicClassInRightFile");
  }
  public void testPublicClassInRightFile2() throws Exception {
    doTest(BASE_PATH + "/publicClassInRightFile/x/Y.java", BASE_PATH + "/publicClassInRightFile");
  }

  private void doTest(String filePath, String dir) throws Exception {
    File basePath = new File(getTestDataPath());
    File currentDir = new File(basePath, dir);
    Files.walkFileTree(currentDir.toPath(), new SimpleFileVisitor<Path>(){
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        File additionalFile = file.toFile();
        String additionalPath = "/" + FileUtil.toSystemIndependentName(FileUtil.getRelativePath(basePath, additionalFile));
        if (!filePath.equals(additionalPath)) {
          myFixture.addFileToProject("/" + FileUtil.toSystemIndependentName(FileUtil.getRelativePath(currentDir, additionalFile)), 
                                     FileUtil.loadFile(additionalFile));
        }
        return super.visitFile(file, attrs);
      }
    });
    
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting(false, false, false);
  }
}
