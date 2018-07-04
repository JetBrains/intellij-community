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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public class JavaAPIUsagesInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    final Java15APIUsageInspection usageInspection = new Java15APIUsageInspection();
    doTest("usage1.5/" + getTestName(true), new LocalInspectionToolWrapper(usageInspection), "java 1.5");
  }

  public void testConstructor() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_4, this::doTest);
  }

  public void testIgnored() {
    doTest();
  }

  public void testAnnotation() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public void testDefaultMethods() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public void testOverrideAnnotation() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public void testRawInheritFromNewlyGenerified() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  //generate apiXXX.txt
  /*
  //todo exclude inheritors of ConcurrentMap#putIfAbsent
  public void testCollectSinceApiUsages() {
    final String version = "9";
    final LinkedHashSet<String> notDocumented = new LinkedHashSet<String>();
    final ContentIterator contentIterator = new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(getProject()).findFile(fileOrDir);
        if (file instanceof PsiJavaFile) {
          file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
              super.visitElement(element);
              if (isDocumentedSinceApi(element)) {
                System.out.println(Java15APIUsageInspection.getSignature((PsiMember)element));
              }
            }

            public boolean isDocumentedSinceApi(PsiElement element) {
              if (element instanceof PsiDocCommentOwner) {
                final PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
                if (comment != null) {
                  for (PsiDocTag tag : comment.getTags()) {
                    if (Comparing.strEqual(tag.getName(), "since")) {
                      final PsiDocTagValue value = tag.getValueElement();
                      if (value != null && value.getText().equals(version)) {
                        return true;
                      }
                      break;
                    }
                  }
                }
              }
              return false;
            }
          });
        }
        return true;
      }
    };
    final VirtualFile srcFile = JarFileSystem.getInstance().findFileByPath("c:/tools/jdk9/lib/src.zip!/");
    assert srcFile != null;
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator);

    notDocumented.forEach(System.out::println);
  }

  @Override
  protected void setUpJdk() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final Sdk sdk = JavaSdk.getInstance().createJdk("9.0", "c:/tools/jdk9/", false);
    for (Module module : modules) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
    }
  }*/
}
