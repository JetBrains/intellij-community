// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTestFrameworkCompilingIntegrationTest extends AbstractTestFrameworkIntegrationTest {
  @Override
  protected boolean isCreateProjectFileExplicitly() {
    return false;
  }

  private CompilerTester myCompilerTester;

  protected abstract String getTestContentRoot();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupModule();

    PathMacros pathMacros = PathMacros.getInstance();
    String oldMacroValue = pathMacros.getValue(PathMacrosImpl.MAVEN_REPOSITORY);
    pathMacros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, getDefaultMavenRepositoryPath());
    try {
      myCompilerTester = new CompilerTester(myModule);
      assertThat(myCompilerTester.rebuild().stream()
                   .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
                   .collect(Collectors.toSet())).isEmpty();
    }
    finally {
      pathMacros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, oldMacroValue);
    }
  }

  @NotNull
  @Override
  protected LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  protected String getDefaultMavenRepositoryPath() {
    String root = System.getProperty("user.home", null);
    return (root == null ? Paths.get(".m2") : Paths.get(root, ".m2")).resolve("repository").toAbsolutePath().toString();
  }

  protected void setupModule() throws Exception {
    ModuleRootModificationUtil.updateModel(myModule, model -> {
      model.addContentEntry(getTestContentRoot()).addSourceFolder(getTestContentRoot() + "/test", true);
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myCompilerTester.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
