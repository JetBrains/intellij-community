// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaASTFactory;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.workspaceModel.ide.WorkspaceModelTopics;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class JavaParsingTestCase extends ParsingTestCase {
  private LanguageLevel myLanguageLevel;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public JavaParsingTestCase(String dataPath) {
    this(dataPath, "java", new JavaParserDefinition());
  }

  public JavaParsingTestCase(String dataPath, String fileExt, ParserDefinition... parserDefinitions) {
    super("psi/" + dataPath, fileExt, parserDefinitions);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();
    getProject().registerService(WorkspaceModelTopics.class, new WorkspaceModelTopics());
    getProject().registerService(LanguageLevelProjectExtension.class, new LanguageLevelProjectExtensionImpl(getProject()));
    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new JavaASTFactory());
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    PsiFile file = super.createFile(name, text);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    return file;
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  protected void doParserTest(String text, Consumer<PsiBuilder> parser) {
    String name = getTestName(false);
    myFile = createPsiFile(name, text, parser);
    myFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    try {
      checkResult(name, myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static IFileElementType TEST_FILE_ELEMENT_TYPE = null;
  private static Consumer<PsiBuilder> TEST_PARSER;

  private PsiFile createPsiFile(String name, String text, Consumer<PsiBuilder> parser) {
    if (TEST_FILE_ELEMENT_TYPE == null) {
      TEST_FILE_ELEMENT_TYPE = new MyIFileElementType();
    }

    TEST_PARSER = parser;

    LightVirtualFile virtualFile = new LightVirtualFile(name + '.' + myFileExt, JavaFileType.INSTANCE, text, -1);
    FileViewProvider viewProvider = new SingleRootFileViewProvider(PsiManager.getInstance(getProject()), virtualFile, true);
    return new PsiJavaFileImpl(viewProvider) {
      @NotNull
      @Override
      protected FileElement createFileElement(@NotNull CharSequence text) {
        return new FileElement(TEST_FILE_ELEMENT_TYPE, text);
      }
    };
  }

  private static PsiBuilder createBuilder(ASTNode chameleon) {
    PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    builder.setDebugMode(true);
    return builder;
  }

  private static class MyIFileElementType extends IFileElementType {
    MyIFileElementType() {
      super("test.java.file", JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon);

      PsiBuilder.Marker root = builder.mark();
      TEST_PARSER.accept(builder);
      if (!builder.eof()) {
        PsiBuilder.Marker unparsed = builder.mark();
        while (!builder.eof()) builder.advanceLexer();
        unparsed.error("Unparsed tokens");
      }
      root.done(this);

      ASTNode rootNode = builder.getTreeBuilt();
      return rootNode.getFirstChildNode();
    }
  }
}