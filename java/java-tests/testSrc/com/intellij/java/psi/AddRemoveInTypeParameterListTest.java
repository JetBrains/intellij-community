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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class AddRemoveInTypeParameterListTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  @Test
  public void testAdd() throws IncorrectOperationException {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)createLightFile("Test.java", "class Test extends Type {\n}");
      PsiClass aClass = file.getClasses()[0];
      PsiJavaCodeReferenceElement ref = aClass.getExtendsList().getReferenceElements()[0];
      PsiReferenceParameterList list = ref.getParameterList();

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(projectFixture.get().getProject());

      PsiTypeElement typeElement1 = factory.createTypeElement(factory.createTypeFromText("A", null));
      list.add(typeElement1);

      assertEquals("class Test extends Type<A> {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);

      PsiTypeElement typeElement2 = factory.createTypeElement(factory.createTypeFromText("B", null));
      list.add(typeElement2);

      assertEquals("class Test extends Type<A, B> {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);

      PsiTypeElement typeElement3 = factory.createTypeElement(factory.createTypeFromText("C", null));
      list.addAfter(typeElement3, null);

      assertEquals("class Test extends Type<C, A, B> {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);
    });
  }

  @Test
  public void testRemove() throws IncorrectOperationException {
    final PsiJavaFile file = ReadAction.computeBlocking(() -> (PsiJavaFile)createLightFile("Test.java", "class Test extends Type<A, B, C, D> {\n}"));
    final PsiTypeElement[] parms = ReadAction.computeBlocking(() -> {
      PsiClass aClass = file.getClasses()[0];
      PsiJavaCodeReferenceElement ref = aClass.getExtendsList().getReferenceElements()[0];
      PsiReferenceParameterList list = ref.getParameterList();
      return list.getTypeParameterElements();
    });

    WriteCommandAction.runWriteCommandAction(null, () -> {
      parms[0].delete();

      assertEquals("class Test extends Type< B, C, D> {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);

      parms[2].delete();

      assertEquals("class Test extends Type< B,  D> {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);

      parms[3].delete();

      assertEquals("class Test extends Type< B  > {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);

      parms[1].delete();
    });


    ReadAction.runBlocking(() -> {
      assertEquals("class Test extends Type {\n}", file.getText());
      PsiTestUtil.checkFileStructure(file);
    });
  }

  private static PsiFile createLightFile(String fileName, String text) {
    Project project = projectFixture.get().getProject();
    return PsiFileFactory.getInstance(project).createFileFromText(fileName, FileTypeManager.getInstance().getFileTypeByFileName(fileName),
                                                                  text, LocalTimeCounter.currentTime(), false, false);
  }
}
