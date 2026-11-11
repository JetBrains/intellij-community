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
package com.intellij.java.psi;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class LightVirtualFileLeaksTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private String myTestName;
  private Key<Boolean> myKey;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myTestName = testInfo.getTestMethod().orElseThrow().getName();
    myKey = Key.create(myTestName);
  }

  @Test
  public void testDoNotLeakViaDocument() {
    createFileWithDocument();
    checkLeak();
  }

  private void createFileWithDocument() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Document document = ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(createLightFile()));
    assertNotNull(document);
    PsiJavaFile psiFile = ReadAction.computeBlocking(() -> (PsiJavaFile)PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(document));
    assertSame(document, PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(psiFile));
  }

  private void checkLeak() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GCUtil.tryGcSoftlyReachableObjects();
    LeakHunter.checkLeak(myFixture.getProject(), LightVirtualFile.class, vf -> vf.getUserData(myKey) == Boolean.TRUE);
  }

  @Test
  public void testDoNotLeakViaExternalAnnotations() {
    queryExternalAnnotations();
    checkLeak();
  }

  private void queryExternalAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightVirtualFile vFile = createLightFile();
    ReadAction.runBlocking(() -> assertThat(AnnotationUtil.getAllAnnotations(((PsiJavaFile)PsiManager.getInstance(myFixture.getProject()).findFile(vFile)).getClasses()[0], true, null)).isNullOrEmpty());
  }

  @NotNull
  private LightVirtualFile createLightFile() {
    LightVirtualFile vFile = new LightVirtualFile(myTestName + ".java", JavaFileType.INSTANCE, "class Foo {}");
    vFile.putUserData(myKey, Boolean.TRUE);
    return vFile;
  }
}
