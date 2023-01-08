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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ref.GCUtil;
import org.jetbrains.annotations.NotNull;

public class LightVirtualFileLeaksTest extends LightJavaCodeInsightFixtureTestCase {
  private Key<Boolean> myKey;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myKey = Key.create(getName());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7_ANNOTATED;
  }

  public void testDoNotLeakViaDocument() {
    createFileWithDocument();
    checkLeak();
  }

  private void createFileWithDocument() {
    Document document = FileDocumentManager.getInstance().getDocument(createLightFile());
    assertNotNull(document);
    PsiJavaFile psiFile = (PsiJavaFile)PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    assertSame(document, PsiDocumentManager.getInstance(getProject()).getDocument(psiFile));
  }

  private void checkLeak() {
    GCUtil.tryGcSoftlyReachableObjects();
    LeakHunter.checkLeak(getProject(), LightVirtualFile.class, vf -> vf.getUserData(myKey) == Boolean.TRUE);
  }

  public void testDoNotLeakViaExternalAnnotations() {
    queryExternalAnnotations();
    checkLeak();
  }

  private void queryExternalAnnotations() {
    LightVirtualFile vFile = createLightFile();
    assertEmpty(AnnotationUtil.getAllAnnotations(((PsiJavaFile)getPsiManager().findFile(vFile)).getClasses()[0], true, null));
  }

  @NotNull
  private LightVirtualFile createLightFile() {
    LightVirtualFile vFile = new LightVirtualFile(getName() + ".java", JavaFileType.INSTANCE, "class Foo {}");
    vFile.putUserData(myKey, Boolean.TRUE);
    return vFile;
  }
}
