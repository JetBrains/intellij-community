// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AnnotateAsNullMarkedFixPackageTest {
  private static final DefaultLightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0");
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Language("JAVA")
  private static final String ENCLOSING_CLASS_FILE_TEXT = """
    package com.example;
    
    class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}
    
    public class Enclosing {
      public void foo(NonNullHolder< <caret>String> holder) {
      }
    }
    """;

  @Test
  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsMissing() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);

    runAnnotatePackageAsNullMarkedAction();

    assertJavaFileContent("com/example/package-info.java", """
      @NullMarked
      package com.example;
      
      import org.jspecify.annotations.NullMarked;""");
  }

  @Test
  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsPresent() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);
    codeInsightFixture.get().addFileToProject("com/example/package-info.java", "package com.example;");

    runAnnotatePackageAsNullMarkedAction();

    assertJavaFileContent("com/example/package-info.java", """
        @NullMarked
        package com.example;
        
        import org.jspecify.annotations.NullMarked;""");
  }

  @Test
  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsPresentAndContainsNullUnmarkedAnnotation() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);
    codeInsightFixture.get().addFileToProject("com/example/package-info.java", """
      @NullUnmarked
      package com.example;
      
      import org.jspecify.annotations.NullUnmarked;""");

    runAnnotatePackageAsNullMarkedAction();

    assertJavaFileContent("com/example/package-info.java", """
      @NullMarked
      package com.example;
      
      import org.jspecify.annotations.NullMarked;
      import org.jspecify.annotations.NullUnmarked;""");
  }

  private void runAnnotatePackageAsNullMarkedAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new NullableStuffInspection());
    var intentionAction = myFixture.findSingleIntention("Annotate container as '@NullMarked'");
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Annotate package 'com.example' as '@NullMarked'")));
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intentionAction);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
  }

  private void configureJavaFileFromPathAndText(String path, @Language("JAVA") String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject(path, text).getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
  }

  private void assertJavaFileContent(String path, @Language("JAVA") String expected) {
    codeInsightFixture.get().checkResult(path, expected, false);
  }
}
