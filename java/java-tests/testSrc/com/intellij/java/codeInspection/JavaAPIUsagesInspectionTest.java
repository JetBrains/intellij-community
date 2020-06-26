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
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaAPIUsagesInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/usage1.5/";
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new Java15APIUsageInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testConstructor() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_4, getTestRootDisposable());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIgnored() {
    myFixture.addClass("package java.awt.geom; public class GeneralPath {public void moveTo(int x, int y){}}");
    doTest(); 
  }
  public void testAnnotation() { doTest(); }
  public void testDefaultMethods() { doTest(); }
  public void testOverrideAnnotation() { doTest(); }
  public void testRawInheritFromNewlyGenerified() {
    myFixture.addClass("package javax.swing; public class AbstractListModel<K> {}");
    doTest(); 
  }
  
  private void doTest() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_6, getTestRootDisposable());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  //generate apiXXX.txt
  //configure jdk and set test project descriptor
  /* private static final String JDK_HOME = "/home/me/java/jdk-15";
  private static final String PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-14.0.1";
  private static final LanguageLevel LANGUAGE_LEVEL = LanguageLevel.JDK_14_PREVIEW;
  private static final String VERSION = "15";
  private static final LightProjectDescriptor API_VERSION_PROJECT_DESCRIPTOR = new LightProjectDescriptor() {
    @Nullable
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk(VERSION, JDK_HOME + "/", false);
    }
  };

  //todo exclude inheritors of ConcurrentMap#putIfAbsent
  public void testCollectSinceApiUsages() {
    VfsRootAccess.allowRootAccess("/");
    final LinkedHashSet<String> notDocumented = new LinkedHashSet<String>();
    final Set<String> previews = new HashSet<>();
    final ContentIterator previewContentIterator = new ContentIterator() {
      @Override
      public boolean processFile(@NotNull VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(JavaAPIUsagesInspectionTest.this.getProject()).findFile(fileOrDir);
        PsiTreeUtil.findChildrenOfAnyType(file, PsiMember.class)
          .stream()
          .filter(member -> member.hasAnnotation(HighlightingFeature.JDK_INTERNAL_PREVIEW_FEATURE))
          .filter(member -> getLanguageLevel(member) == LANGUAGE_LEVEL)
          .map(e -> Java15APIUsageInspection.getSignature(e))
          .forEach(previews::add);
        return true;
      }

      @Nullable
      private LanguageLevel getLanguageLevel(@NotNull final PsiMember e) {
        final PsiAnnotation annotation = HighlightUtil.getPreviewFeatureAnnotation(e);
        if (annotation == null) return null;

        final HighlightingFeature feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation);
        return feature == null ? null : feature.getLevel();
      }
    };
    if (LANGUAGE_LEVEL.isPreview()) {
      final VirtualFile previewSrcFile = JarFileSystem.getInstance().findFileByPath(PREVIEW_JDK_HOME + "/lib/src.zip!/");
      assert previewSrcFile != null;
      VfsUtilCore.iterateChildrenRecursively(previewSrcFile, VirtualFileFilter.ALL, previewContentIterator);
    }

    final ContentIterator contentIterator = new ContentIterator() {
      @Override
      public boolean processFile(@NotNull VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(getProject()).findFile(fileOrDir);
        if (file instanceof PsiJavaFile) {
          file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
              super.visitElement(element);
              if (isDocumentedSinceApi(element)) {
                final String signature = Java15APIUsageInspection.getSignature((PsiMember)element);
                if (!previews.contains(signature)) {
                  System.out.println(signature);
                }
              }
            }

            public boolean isDocumentedSinceApi(PsiElement element) {
              if (element instanceof PsiDocCommentOwner) {
                final PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
                if (comment != null) {
                  for (PsiDocTag tag : comment.getTags()) {
                    if (Comparing.strEqual(tag.getName(), "since")) {
                      final PsiDocTagValue value = tag.getValueElement();
                      if (value != null && value.getText().equals(VERSION)) {
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
    final VirtualFile srcFile = JarFileSystem.getInstance().findFileByPath(JDK_HOME + "/lib/src.zip!/");
    assert srcFile != null;
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator);

    notDocumented.forEach(System.out::println);
  }*/
}
