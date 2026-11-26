/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.GenerateDelegateHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class DelegateMethodsTest {
  private static final String BASE_PATH = "/codeInsight/delegateMethods/";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testMethodWithJavadoc() { doTest(); }
  @Test
  public void testStaticMemberWithNonStaticField() { doTest(); }
  @Test
  public void testTypeParam() { doTest(); }
  @Test
  public void testExistingMethodWithAnnotation() { doTest(); }
  @Test
  public void testDelegateToContainingClassField() { doTest(); }
  @Test
  public void testDelegateFromStaticClassField() { doTest(); }

  @Test
  public void testCopyJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testName = myFixture.getTestName(false);
    myFixture.configureByFile(BASE_PATH + "before" + testName + ".java");
    myFixture.runInCommand(() -> {
      GenerateDelegateHandler handler = new GenerateDelegateHandler();
      try {
        handler.setToCopyJavaDoc(true);
        handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      }
      finally {
        handler.setToCopyJavaDoc(false);
      }
      myFixture.checkResultByFile(BASE_PATH + "after" + testName + ".java");
    });
  }

  @Test
  public void testSuperSubstitution() { doTest(); }
  @Test
  public void testCopyAnnotationWithParams() { doTest(); }
  @Test
  public void testMultipleOverrideAnnotations() { doTest(); }
  @Test
  public void testStripSuppressWarningsAnnotation() { doTest(); }
  @Test
  public void testDoNotOverrideFinal() { doTest(); }
  @Test
  public void testEnsureCorrectOverride() { doTest(); }
  @Test
  public void testAllowDelegateToFinal() { doTest(); }
  @Test
  public void testDelegateWithSubstitutionOverrides() { doTest(); }
  @Test
  public void testDelegateWithSubstitutionNoOverrides() { doTest(); }
  @Test
  public void testSingleField() { doTest(); }
  @Test
  public void testInsideLambdaWithNonInferredTypeParameters() { doTest(); }
  @Test
  public void testTypeUseAnnotationsInReturnType() { doTest(); }
  @Test
  public void testTypeUseAnnotationsInReturnType2() { doTest(); }
  @Test
  public void testTypeUseAnnotationsInReturnType3() {
    VirtualFile sourceRoot = projectFixture.get().getSourceRoot();
    VfsTestUtil.createFile(sourceRoot, "pkg/Foo.java",
                      """
                        package pkg;
                        import java.lang.annotation.*;
                        @Target({ElementType.TYPE_USE})
                        @interface Foo {}""");
    VfsTestUtil.createFile(sourceRoot, "Abstract.java",
                      """
                        import java.io.Writer;
                        import pkg.Foo;
                        abstract class Abstract {
                            @Foo public abstract Writer getWriter();
                        }
                        """);
    doTest(); 
  }
  @Test
  public void testTypeUseAnnotationsInArrayParameter() { doTest(); }
  @Test
  public void testPreserveEllipsisType() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testName = myFixture.getTestName(false);
    myFixture.configureByFile(BASE_PATH + "before" + testName + ".java");
    myFixture.runInCommand(() -> {
      new GenerateDelegateHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.checkResultByFile(BASE_PATH + "after" + testName + ".java");
    });
  }
}