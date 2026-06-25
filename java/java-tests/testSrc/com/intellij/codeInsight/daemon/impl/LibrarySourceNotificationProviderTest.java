// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class LibrarySourceNotificationProviderTest extends LightJavaCodeInsightFixtureTestCase {

  public void testAugmentedLibrarySourceDoesNotShowMismatchNotification() throws IOException {
    PsiAugmentProvider.EP_NAME.getPoint().registerExtension(new GeneratedMembersAugmentProvider(), myFixture.getTestRootDisposable());

    String source = """
      package lib;
      public class Foo {
      }
      """;
    String sourceToCompile = """
      package lib;
      public class Foo {
        public int generatedField;
        public void generatedMethod() {}
      }
      """;
    PsiJavaFile librarySource = prepareLibrarySource(source, sourceToCompile);
    assertNull(collectNotificationData(librarySource));
  }

  public void testMismatchingLibrarySourceShowsNotification() throws IOException {
    String source = """
      package lib;
      public class Foo {
      }
      """;
    String sourceToCompile = """
      package lib;
      public class Foo {
        public int generatedField;
        public void generatedMethod() {}
      }
      """;
    PsiJavaFile sourceFile = prepareLibrarySource(source, sourceToCompile);
    assertNotNull(collectNotificationData(sourceFile));
  }

  /**
   * Prepares a library source file in a temporary directory, compiles it, and adds it to the project as a library.
   *
   * @param source          the content of the source file to be added to the library.
   * @param sourceToCompile the content of the source file to be compiled and added as class files to the library.
   * @return the prepared {@code PsiJavaFile} instance representing the library source.
   * @throws IOException if an error occurs during file creation or writing.
   */
  private PsiJavaFile prepareLibrarySource(String source, String sourceToCompile) throws IOException {
    String libraryName = getTestName(true);
    File libraryRoot = FileUtil.createTempDirectory(libraryName, null);
    Disposer.register(myFixture.getTestRootDisposable(), () -> FileUtil.delete(libraryRoot));

    File classesPath = new File(libraryRoot, "classes");
    File sourcesPath = new File(libraryRoot, "sources");
    File compilationSourcePath = new File(libraryRoot, "compiler/lib/Foo.java");
    File librarySourcePath = new File(sourcesPath, "lib/Foo.java");

    FileUtil.createDirectory(classesPath);
    FileUtil.createParentDirs(compilationSourcePath);
    FileUtil.writeToFile(compilationSourcePath, sourceToCompile);
    FileUtil.createParentDirs(librarySourcePath);
    FileUtil.writeToFile(librarySourcePath, source);

    IdeaTestUtil.compileFile(compilationSourcePath, classesPath);

    VirtualFile classesDir = refreshAndFind(classesPath);
    VirtualFile sourcesDir = refreshAndFind(sourcesPath);
    VirtualFile librarySourceFile = refreshAndFind(librarySourcePath);
    PsiTestUtil.addProjectLibrary(getModule(), libraryName, List.of(classesDir), List.of(sourcesDir));
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    PsiJavaFile librarySource = (PsiJavaFile)PsiManager.getInstance(getProject()).findFile(librarySourceFile);
    assertNotNull(librarySource);
    assertTrue(FileIndexFacade.getInstance(getProject()).isInLibrarySource(librarySource.getVirtualFile()));
    return librarySource;
  }

  private static VirtualFile refreshAndFind(File path) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path);
    assertNotNull(path.getPath(), file);
    return file;
  }

  private static Function<? super FileEditor, ? extends JComponent> collectNotificationData(PsiJavaFile librarySource) {
    return createLibrarySourceNotificationProvider().collectNotificationData(librarySource.getProject(), librarySource.getVirtualFile());
  }

  private static final class GeneratedMembersAugmentProvider extends PsiAugmentProvider {
    @Override
    protected <Psi extends PsiElement> @NotNull List<Psi> getAugments(@NotNull PsiElement element,
                                                                      @NotNull Class<Psi> type,
                                                                      @Nullable String nameHint) {
      if (!(element instanceof PsiClass psiClass) || element instanceof PsiCompiledElement ||
          !Objects.equals("lib.Foo", psiClass.getQualifiedName()) || !(psiClass.getContainingFile() instanceof PsiJavaFile)) {
        return Collections.emptyList();
      }

      if (type == PsiField.class) {
        var field = new LightFieldBuilder(psiClass.getManager(), "generatedField", PsiTypes.intType()).setContainingClass(psiClass);
        return List.of(type.cast(field));
      }

      if (type == PsiMethod.class) {
        LightMethodBuilder method = new LightMethodBuilder(psiClass.getManager(), "generatedMethod");
        method.setMethodReturnType(PsiTypes.voidType());
        method.setContainingClass(psiClass);
        return List.of(type.cast(method));
      }

      return Collections.emptyList();
    }
  }

  private static EditorNotificationProvider createLibrarySourceNotificationProvider() {
    try {
      String providerFqn = "com.intellij.codeInsight.daemon.impl.LibrarySourceNotificationProvider";
      return (EditorNotificationProvider)Class.forName(providerFqn)
        .getDeclaredConstructor().newInstance();
    }
    catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
