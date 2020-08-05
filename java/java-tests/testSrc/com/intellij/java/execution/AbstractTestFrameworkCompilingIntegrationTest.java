// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.OpenProjectTaskBuilder;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public abstract class AbstractTestFrameworkCompilingIntegrationTest extends AbstractTestFrameworkIntegrationTest {
  @Override
  protected @NotNull OpenProjectTaskBuilder getOpenProjectOptions() {
    return super.getOpenProjectOptions().componentStoreLoadingEnabled(false);
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
      String errors = myCompilerTester.rebuild().stream()
        .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
        .map(message -> message.toString())
        .collect(Collectors.joining("\n"));
      if (!errors.isEmpty()) {
        Path optionDir = PathManager.getConfigDir().resolve("options");
        throw new AssertionError(errors + getConfigFile("jdk.table.xml", optionDir) + getConfigFile("path.macros.xml", optionDir));
      }
    }
    finally {
      pathMacros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, oldMacroValue);
    }
  }

  private @NotNull static String getConfigFile(@NotNull String name, @NotNull Path optionDir) throws IOException {
    try {
      return "\n---\n" + name + " content: " + PathKt.readText(optionDir.resolve(name)) + "\n---\n";
    }
    catch (NoSuchFileException e) {
      return name + " doesn't exist";
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
