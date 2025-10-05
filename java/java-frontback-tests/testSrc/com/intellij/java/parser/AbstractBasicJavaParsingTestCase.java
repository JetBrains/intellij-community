// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.PathJavaTestUtil;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;
import com.intellij.platform.syntax.tree.SyntaxNode;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class AbstractBasicJavaParsingTestCase extends ParsingTestCase {

  private final AbstractBasicJavaParsingTestConfigurator myConfigurator;
  private SyntaxNode myKmpTree;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public AbstractBasicJavaParsingTestCase(String dataPath, AbstractBasicJavaParsingTestConfigurator configurator) {
    this(dataPath, "java", configurator);
  }

  public AbstractBasicJavaParsingTestCase(String dataPath, String fileExt, AbstractBasicJavaParsingTestConfigurator configurator) {
    super("psi/" + dataPath, fileExt, new JavaParserDefinition());
    myConfigurator = configurator;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getProject().registerService(WorkspaceModelTopics.class, new WorkspaceModelTopics());
    addExplicit(LanguageSyntaxDefinitions.getINSTANCE(), JavaLanguage.INSTANCE, new JavaSyntaxDefinitionExtension());
    myConfigurator.setUp(this);
  }

  @Override
  protected String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public final <T> void addExplicit(@NotNull LanguageExtension<T> collector, @NotNull Language language, @NotNull T object) {
    addExplicitExtension(collector, language, object);
  }

  public final <T> void addExplicit(@NotNull ExtensionPointName<T> extensionPointName, @NotNull T extension) {
    registerExtension(extensionPointName, extension);
  }

  public final <T> void addExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> extensionClass) {
    super.registerExtensionPoint(extensionPointName, extensionClass);
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    PsiFile file = super.createFile(name, text);
    myConfigurator.configure(file);
    return file;
  }


  protected void doParserTest(BasicJavaParserUtil.@NotNull ParserWrapper parser) {
    String name = getTestName(false);
    try {
      String text = loadFile(name + "." + myFileExt);
      doParserTest(text, parser);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myConfigurator.setLanguageLevel(languageLevel);
  }

  @Override
  protected void checkResult(@NotNull @TestDataFile String targetDataName,
                             @NotNull PsiFile file) throws IOException {
    if (myConfigurator.checkPsi()) {
      super.checkResult(targetDataName, file);
    }

    String treeDump = DebugUtil.nodeTreeAsElementTypeToString(file.getNode(), !skipSpaces()).trim();
    assertSameLinesWithFile(getExpectedFileName(targetDataName), treeDump);

    checkKmpTree(targetDataName);
  }

  private void checkKmpTree(@NotNull String targetDataName) {
    String kmpTreeDump = JavaTestSyntaxParsingUtil.dumpTree(myKmpTree);

    String expectedFileName = getExpectedFileName(targetDataName);
    String expectedText;
    try {
      expectedText = FileUtil.loadFile(new File(expectedFileName), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    String normalizedExpected = JavaTestSyntaxParsingUtil.removeEmptyListLines(expectedText);
    assertSameLines(normalizedExpected, kmpTreeDump);
  }

  private @NotNull String getExpectedFileName(@NotNull String targetDataName) {
    return myFullDataPath + File.separatorChar + targetDataName + "_node.txt";
  }

  @Override
  protected @NotNull PsiFile parseFile(@NotNull String name, @NotNull String text) {
    myKmpTree = myConfigurator.createFileSyntaxNode(text, null);
    return super.parseFile(name, text);
  }

  protected void doParserTest(@NotNull String text,
                              BasicJavaParserUtil.@NotNull ParserWrapper parser) {
    String name = getTestName(false);
    myFile = myConfigurator.createPsiFile(this, name, text, parser);
    myKmpTree = myConfigurator.createFileSyntaxNode(text, parser);
    try {
      checkResult(name, myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}