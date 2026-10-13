// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.PathJavaTestUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.syntax.tree.SyntaxNode;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.UsefulTestCase.assertSameLines;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

public abstract class AbstractBasicJavaParsingTestCase {
  private static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture;
  private final AbstractBasicJavaParsingTestConfigurator myConfigurator;
  @Nullable
  private SyntaxNode myKmpTree;

  public AbstractBasicJavaParsingTestCase(String dataPath, AbstractBasicJavaParsingTestConfigurator configurator) {
    this(dataPath, "java", configurator);
  }

  public AbstractBasicJavaParsingTestCase(String dataPath, String fileExt, AbstractBasicJavaParsingTestConfigurator configurator) {
    parsingFixture = parsingFixture(projectFixture, PathJavaTestUtil.getCommunityJavaTestDataPath() + "/" + "psi/" + dataPath, fileExt,
                                    new JavaParserDefinition());
    myConfigurator = configurator;
  }

  @BeforeEach
  protected void setUpJavaParsing() {
    myConfigurator.setUp(this);
  }

  public final @NotNull Project getProject() {
    return parsingFixture.get().getProject();
  }

  public final <T> void addExplicit(@NotNull LanguageExtension<T> collector, @NotNull Language language, @NotNull T object) {
    parsingFixture.get().addExplicitExtension(collector, language, object);
  }

  public final <T> void addExplicit(@NotNull ExtensionPointName<T> extensionPointName, @NotNull T extension) {
    ExtensionTestUtil.addExtensions(extensionPointName, List.of(extension), parsingFixture.get().getTestRootDisposable());
  }

  public final <T> void addExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> extensionClass) {
    ExtensionsAreaImpl area = (ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea();
    if (!area.hasExtensionPoint(extensionPointName)) {
      area.registerExtensionPoint(extensionPointName, extensionClass.getName(), ExtensionPoint.Kind.INTERFACE,
                                  parsingFixture.get().getTestRootDisposable());
    }
  }

  public final void clearCachesOfLanguageExtension(@NotNull Language language, @NotNull LanguageExtension<?> instance) {
    instance.clearCache(language);
    Disposer.register(parsingFixture.get().getTestRootDisposable(), () -> instance.clearCache(language));
  }

  protected final @NotNull String getTestName() {
    return parsingFixture.get().getTestName();
  }

  protected final @NotNull String getFileExt() {
    return parsingFixture.get().getFileExt();
  }

  protected final @NotNull String loadFile(@NotNull @TestDataFile String name) {
    return parsingFixture.get().loadFile(name);
  }

  protected @NotNull PsiFile createPsiFile(@NotNull String name, @NotNull String text) {
    PsiFile file = parsingFixture.get().createPsiFile(name, text);
    myConfigurator.configure(file);
    return file;
  }

  protected void doTest(boolean checkResult) {
    ParsingTestFixture fixture = parsingFixture.get();
    String name = fixture.getTestName();
    String text = fixture.loadFile(name + "." + fixture.getFileExt());
    PsiFile file = parseFile(name, text);
    if (checkResult) {
      checkResult(name, file);
    }
    else {
      ReadAction.computeBlocking(
        () -> ParsingTestFixture.toParseTreeText(file, fixture.getSkipSpaces(), fixture.getIncludeRanges()));
    }
  }

  protected void doParserTest(JavaParserUtil.@NotNull ParserWrapper parser) {
    doParserTest((Object)parser);
  }

  protected void doParserTest(@NotNull Object parser) {
    String name = getTestName();
    String text = loadFile(name + "." + getFileExt());
    doParserTest(text, parser);
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myConfigurator.setLanguageLevel(languageLevel);
  }

  protected void checkResult(@NotNull @TestDataFile String targetDataName,
                             @NotNull PsiFile file) {
    ParsingTestFixture fixture = parsingFixture.get();
    if (myConfigurator.checkPsi()) {
      fixture.checkResult(targetDataName, file);
    }

    String treeDump = ReadAction.computeBlocking(
      () -> DebugUtil.nodeTreeAsElementTypeToString(file.getNode(), !fixture.getSkipSpaces()).trim());
    PlatformTestUtil.assertSameLinesWithFile(getExpectedFileName(targetDataName), treeDump);

    if (myKmpTree == null) return;
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
    return parsingFixture.get().getFullDataPath() + File.separatorChar + targetDataName + "_node.txt";
  }

  protected @NotNull PsiFile parseFile(@NotNull String name, @NotNull String text) {
    myKmpTree = myConfigurator.createFileSyntaxNode(text, null);
    ParsingTestFixture fixture = parsingFixture.get();
    PsiFile psiFile = myConfigurator.createPsiFileForFullTestFile(this, name, text);
    if (psiFile != null) {
      fixture.setFile(psiFile);
      return psiFile;
    }
    return fixture.checkParsedFile(createPsiFile(name, text), text);
  }

  protected void doParserTest(@NotNull String text,
                              JavaParserUtil.@NotNull ParserWrapper parser) {
    doParserTest(text, (Object)parser);
  }

  protected void doParserTest(@NotNull String text,
                              @NotNull Object parser) {
    ParsingTestFixture fixture = parsingFixture.get();
    String name = fixture.getTestName();
    PsiFile file = ReadAction.computeBlocking(() -> myConfigurator.createPsiFile(this, name, text, parser));
    fixture.setFile(file);
    if (parser instanceof JavaParserUtil.@NotNull ParserWrapper wrapper) {
      myKmpTree = myConfigurator.createFileSyntaxNode(text, wrapper);
    }
    checkResult(name, file);
  }
}