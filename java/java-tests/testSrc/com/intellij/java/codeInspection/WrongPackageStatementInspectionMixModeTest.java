// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.Objects;

public final class WrongPackageStatementInspectionMixModeTest extends LightJavaCodeInsightFixtureTestCase {

  public static final ProjectDescriptor DESCRIPTOR = new ProjectDescriptor(LanguageLevel.HIGHEST) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        JavaSourceRootProperties properties =
          Objects.requireNonNull(sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES));
        properties.setPackageMatchesDirectory(false);
      }
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new WrongPackageStatementInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }

  public void testWrongPackage() {
    myFixture.configureByText("Test.java", """
        package com.foo.bar;
        
        class Test { }
        """);
    String url = myFixture.getFile().getVirtualFile().getUrl();
    assertEquals("temp:///src/Test.java", url);
    myFixture.checkHighlighting();
  }
}
