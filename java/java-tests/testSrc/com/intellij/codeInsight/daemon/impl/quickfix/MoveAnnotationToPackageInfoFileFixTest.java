// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MoveAnnotationToPackageInfoFileFixTest extends LightJavaCodeInsightFixtureTestCase {
  private static final ProjectDescriptor LIBRARY_PACKAGE_INFO_DESCRIPTOR = new LibraryPackageInfoDescriptor();

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return getTestName(false).equals("LibraryPackageInfo") ? LIBRARY_PACKAGE_INFO_DESCRIPTOR : super.getProjectDescriptor();
  }

  public void testSimple() {
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  public void testImports() {
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

  public void testLibraryPackageInfo() {
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/moveAnnotationToPackage";
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
