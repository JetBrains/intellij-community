// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MoveAnnotationToPackageInfoFileFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private static final TestFixture<LightJavaProject> libraryProjectFixture = lightJavaProjectFixture(new LibraryPackageInfoDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> libraryCodeInsightFixture =
    lightJavaCodeInsightFixture(libraryProjectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    libraryCodeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/moveAnnotationToPackage");
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/moveAnnotationToPackage");
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  @Test
  public void testImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.example.anno; @interface Anno {}");
    myFixture.configureByFile("com/example/Imports.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
                             
         import com.example.anno.Anno;
         
         class Imports {}
         ----------
         @Deprecated @Anno
         package com.example;
         
         import com.example.anno.Anno;""", text);
  }

  @Test
  public void testLibraryPackageInfo() {
    LightJavaCodeInsightTestFixture myFixture = libraryCodeInsightFixture.get();
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  /** Adds a library whose source root has a `package-info.java` of the package `com.example`. */
  private static final class LibraryPackageInfoDescriptor extends ProjectDescriptor {
    LibraryPackageInfoDescriptor() {
      super(LanguageLevel.HIGHEST);
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      VirtualFile parent = Objects.requireNonNull(contentEntry.getFile()).getParent();
      VfsTestUtil.createFile(parent, "librarySources/com/example/package-info.java", "@Deprecated\npackage com.example;\n");
      Library.ModifiableModel library = model.getModuleLibraryTable().createLibrary("library").getModifiableModel();
      library.addRoot(Objects.requireNonNull(parent.findChild("librarySources")), OrderRootType.SOURCES);
      library.commit();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof LibraryPackageInfoDescriptor;
    }

    @Override
    public int hashCode() {
      return LibraryPackageInfoDescriptor.class.hashCode();
    }
  }
}
