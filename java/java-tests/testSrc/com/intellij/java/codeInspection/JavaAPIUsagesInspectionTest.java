// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

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
  private static final String PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-15";
  private static final String JDK_HOME = "/home/me/java/jdk-16";
  private static final LanguageLevel LANGUAGE_LEVEL = LanguageLevel.JDK_15_PREVIEW;
  private static final String VERSION = "16";
  private static final LightProjectDescriptor API_VERSION_PROJECT_DESCRIPTOR = new LightProjectDescriptor() {
    @Override
    public @NotNull Sdk getSdk() {
      return JavaSdk.getInstance().createJdk(VERSION, JDK_HOME + "/", false);
    }
  };

  //todo exclude inheritors of ConcurrentMap#putIfAbsent
  public void testCollectSinceApiUsages() {
    //doCollectSinceApiUsages();
  }

  private void doCollectSinceApiUsages() {
    final Set<String> previews = new HashSet<>();
    final ContentIterator previewContentIterator = new ContentIterator() {
      @Override
      public boolean processFile(@NotNull VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(JavaAPIUsagesInspectionTest.this.getProject()).findFile(fileOrDir);
        PsiTreeUtil.findChildrenOfAnyType(file, PsiMember.class)
          .stream()
          .filter(member -> member.hasAnnotation(HighlightingFeature.JDK_INTERNAL_PREVIEW_FEATURE) ||
                            member.hasAnnotation(HighlightingFeature.JDK_INTERNAL_JAVAC_PREVIEW_FEATURE))
          .filter(member -> getLanguageLevel(member) == LANGUAGE_LEVEL)
          .map(e -> Java15APIUsageInspection.getSignature(e))
          .forEach(previews::add);
        return true;
      }

      @Nullable
      private LanguageLevel getLanguageLevel(@NotNull final PsiMember e) {
        final PsiAnnotation annotation = HighlightingFeature.getPreviewFeatureAnnotation(e);
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
  }

}
