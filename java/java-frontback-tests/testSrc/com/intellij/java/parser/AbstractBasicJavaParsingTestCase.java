// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.PathJavaTestUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.PsiBuilder;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class AbstractBasicJavaParsingTestCase extends ParsingTestCase {

  private final AbstractBasicJavaParsingTestConfigurator myConfigurator;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public AbstractBasicJavaParsingTestCase(String dataPath, AbstractBasicJavaParsingTestConfigurator configurator) {
    this(dataPath, "java", configurator);
  }

  public AbstractBasicJavaParsingTestCase(String dataPath, String fileExt, AbstractBasicJavaParsingTestConfigurator configurator) {
    super("psi/" + dataPath, fileExt, configurator.getJavaParserDefinition());
    myConfigurator = configurator;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getProject().registerService(WorkspaceModelTopics.class, new WorkspaceModelTopics());
    myConfigurator.setUp(this);
  }

  @Override
  protected String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public final <T> void addExplicit(@NotNull LanguageExtension<T> collector, @NotNull Language language, @NotNull T object) {
    addExplicitExtension(collector, language, object);
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    PsiFile file = super.createFile(name, text);
    myConfigurator.configure(file);
    return file;
  }


  protected void doParserTest(Consumer<PsiBuilder> parser) {
    String name = getTestName(false);
    try {
      doParserTest(loadFile(name + "." + myFileExt), parser);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myConfigurator.setLanguageLevel(languageLevel);
  }

  @Override
  protected void checkResult(@NotNull @TestDataFile String targetDataName, @NotNull PsiFile file) throws IOException {
    doCheckResult(myFullDataPath, targetDataName + "_node.txt",
                  DebugUtil.nodeTreeAsElementTypeToString(file.getNode(), !skipSpaces()).trim());
    if (myConfigurator.checkPsi()) {
      super.checkResult(targetDataName, file);
    }
  }

  protected void doParserTest(String text, Consumer<PsiBuilder> parser) {
    String name = getTestName(false);
    myFile = myConfigurator.createPsiFile(this, name, text, parser);
    try {
      checkResult(name, myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}