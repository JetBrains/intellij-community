// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;


public class AnnotateAsNullMarkedFixPackageTest extends LightJavaCodeInsightFixtureTestCase {
  @Language("JAVA")
  private static final String ENCLOSING_CLASS_FILE_TEXT = """
    package com.example;
    
    class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}
    
    public class Enclosing {
      public void foo(NonNullHolder< <caret>String> holder) {
      }
    }
    """;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0");
      }
    };
  }

  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsMissing() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);

    runAnnotatePackageAsNullMarkedAction();

    assertJavaFileContent("com/example/package-info.java", """
      @NullMarked
      package com.example;
      
      import org.jspecify.annotations.NullMarked;""");
  }

  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsPresent() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);
    myFixture.addFileToProject("com/example/package-info.java", "package com.example;");

    runAnnotatePackageAsNullMarkedAction();

    assertJavaFileContent("com/example/package-info.java", """
        @NullMarked
        package com.example;
        
        import org.jspecify.annotations.NullMarked;""");
  }

  public void testAnnotatePackageAsNullMarkedWhenPackageInfoIsPresentAndContainsNullUnmarkedAnnotation() {
    configureJavaFileFromPathAndText("com/example/Enclosing.java", ENCLOSING_CLASS_FILE_TEXT);
    myFixture.addFileToProject("com/example/package-info.java", """
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
    myFixture.enableInspections(new NullableStuffInspection());
    var intentionAction = myFixture.findSingleIntention("Annotate container as '@NullMarked'");
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Annotate package 'com.example' as '@NullMarked'")));
    myFixture.launchAction(intentionAction);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  private void configureJavaFileFromPathAndText(String path, @Language("JAVA") String text) {
    VirtualFile file = myFixture.addFileToProject(path, text).getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
  }

  private void assertJavaFileContent(String path, @Language("JAVA") String expected) {
    myFixture.checkResult(path, expected, false);
  }
}
