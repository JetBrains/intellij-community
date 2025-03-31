// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.lang.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.*;
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
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class JavaParsingTestConfigurator implements AbstractBasicJavaParsingTestConfigurator {
  private LanguageLevel myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();

  @Override
  public ParserDefinition getJavaParserDefinition() {
    return new JavaParserDefinition();
  }

  @Override
  public LanguageSyntaxDefinition getJavaSyntaxDefinition() {
    return new JavaSyntaxDefinitionExtension();
  }

  @Override
  public void setUp(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase) {
    thinJavaParsingTestCase.getProject()
      .registerService(LanguageLevelProjectExtension.class, new LanguageLevelProjectExtensionImpl(thinJavaParsingTestCase.getProject()));

    JavaLanguage language = JavaLanguage.INSTANCE;
    LanguageASTFactory languageASTFactory = LanguageASTFactory.INSTANCE;
    thinJavaParsingTestCase.addExplicit(languageASTFactory, language, new JavaASTFactory());
    LanguageExtension<ElementTypeConverterFactory> elementTypeConverters = ElementTypeConverters.getInstance();

    thinJavaParsingTestCase.addExplicit(elementTypeConverters, language, new JavaElementTypeConverterExtension());
    thinJavaParsingTestCase.addExplicit(elementTypeConverters, language, new JavaTestElementTypeConverterExtension());

    thinJavaParsingTestCase.clearCachesOfLanguageExtension(language, languageASTFactory);
    thinJavaParsingTestCase.clearCachesOfLanguageExtension(language, elementTypeConverters);
  }

  @Override
  public void configure(@NotNull PsiFile file) {
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    LANGUAGE_LEVEL = myLanguageLevel;
  }

  private static final MyIFileElementType myTestFileElementType = new MyIFileElementType();
  private static final SyntaxElementType mySyntaxElementType = new SyntaxElementType("test.java.file");

  private static BasicJavaParserUtil.ParserWrapper TEST_PARSER;
  private static LanguageLevel LANGUAGE_LEVEL;

  @Override
  public @NotNull PsiFile createPsiFile(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase, @NotNull String name, @NotNull String text, BasicJavaParserUtil.@NotNull ParserWrapper parser) {
    TEST_PARSER = parser;

    LightVirtualFile virtualFile = new LightVirtualFile(name + '.' + "java", JavaFileType.INSTANCE, text, -1);
    FileViewProvider viewProvider =
      new SingleRootFileViewProvider(PsiManager.getInstance(thinJavaParsingTestCase.getProject()), virtualFile, true);
    PsiJavaFileImpl file = new PsiJavaFileImpl(viewProvider) {
      @NotNull
      @Override
      protected FileElement createFileElement(@NotNull CharSequence text) {
        return new FileElement(myTestFileElementType, text);
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
  public void setLanguageLevel(@NotNull com.intellij.pom.java.LanguageLevel level) {
    myLanguageLevel = level;
  }

  private static PsiSyntaxBuilder createBuilder(ASTNode chameleon) {
    PsiSyntaxBuilder builder = JavaParserUtil.createSyntaxBuilder(chameleon).getBuilder();
    builder.setDebugMode(true);
    return builder;
  }

  private static class MyIFileElementType extends IFileElementType {
    MyIFileElementType() {
      super("test.java.file", JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      PsiSyntaxBuilder psiBuilder = createBuilder(chameleon);
      SyntaxTreeBuilder builder = psiBuilder.getSyntaxTreeBuilder();
      SyntaxTreeBuilder.Marker root = builder.mark();
      TEST_PARSER.parse(builder, LANGUAGE_LEVEL);
      if (!builder.eof()) {
        SyntaxTreeBuilder.@NotNull Marker unparsed = builder.mark();
        while (!builder.eof()) builder.advanceLexer();
        unparsed.error("Unparsed tokens");
      }
      root.done(mySyntaxElementType);

      ASTNode rootNode = psiBuilder.getTreeBuilt();
      return rootNode.getFirstChildNode();
    }
  }

  private static class JavaTestElementTypeConverterExtension implements ElementTypeConverterFactory {
    private static final @NotNull ElementTypeConverter CONVERTER =
      ElementTypeConverterKt.elementTypeConverterOf(new Pair<>(mySyntaxElementType, myTestFileElementType));

    @Override
    public @NotNull ElementTypeConverter getElementTypeConverter() {
      return CONVERTER;
    }
  }
}
