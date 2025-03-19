// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class JavaParsingTestConfigurator implements AbstractBasicJavaParsingTestConfigurator {
  private LanguageLevel myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();

  @Override
  public ParserDefinition getJavaParserDefinition() {
    return new JavaParserDefinition();
  }

  @Override
  public void setUp(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase) {
    thinJavaParsingTestCase.getProject()
      .registerService(LanguageLevelProjectExtension.class, new LanguageLevelProjectExtensionImpl(thinJavaParsingTestCase.getProject()));
    thinJavaParsingTestCase.addExplicit(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new JavaASTFactory());
  }

  @Override
  public void configure(@NotNull PsiFile file) {
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
  }

  private static IFileElementType TEST_FILE_ELEMENT_TYPE = null;
  private static Consumer<PsiBuilder> TEST_PARSER;


  @Override
  public @NotNull PsiFile createPsiFile(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase, @NotNull String name, @NotNull String text, @NotNull Consumer<PsiBuilder> parser) {
    if (TEST_FILE_ELEMENT_TYPE == null) {
      TEST_FILE_ELEMENT_TYPE = new MyIFileElementType();
    }

    TEST_PARSER = parser;

    LightVirtualFile virtualFile = new LightVirtualFile(name + '.' + "java", JavaFileType.INSTANCE, text, -1);
    FileViewProvider viewProvider =
      new SingleRootFileViewProvider(PsiManager.getInstance(thinJavaParsingTestCase.getProject()), virtualFile, true);
    PsiJavaFileImpl file = new PsiJavaFileImpl(viewProvider) {
      @NotNull
      @Override
      protected FileElement createFileElement(@NotNull CharSequence text) {
        return new FileElement(TEST_FILE_ELEMENT_TYPE, text);
      }
    };
    configure(file);
    return file;
  }

  @Override
  public boolean checkPsi() {
    return true;
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel level) {
    myLanguageLevel = level;
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
