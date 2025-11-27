// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.syntax.element.JavaLanguageLevelProvider;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.tree.SyntaxNode;
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
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldJavaParsingTestConfigurator
  implements AbstractBasicJavaParsingTestConfigurator {
  private LanguageLevel myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();

  private String rootName = "test.java.file";

  public OldJavaParsingTestConfigurator(String name) {
    this.rootName = name;
  }

  public OldJavaParsingTestConfigurator() {
  }

  @Override
  public @NotNull PsiFile createPsiFile(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase,
                                        @NotNull String name,
                                        @NotNull String text,
                                        @NotNull Object parser) {
    MyIFileElementType myIFileElementType = new MyIFileElementType(rootName, (Consumer<PsiBuilder>)parser);


    LightVirtualFile virtualFile = new LightVirtualFile(name + '.' + "java", JavaFileType.INSTANCE, text, -1);
    FileViewProvider viewProvider =
      new SingleRootFileViewProvider(PsiManager.getInstance(thinJavaParsingTestCase.getProject()), virtualFile, true);
    PsiJavaFileImpl file = new PsiJavaFileImpl(viewProvider) {
      @NotNull
      @Override
      protected FileElement createFileElement(@NotNull CharSequence text) {
        return new FileElement(myIFileElementType, text);
      }
    };
    configure(file);
    return file;
  }

  @Override
  public @Nullable SyntaxNode createFileSyntaxNode(@NotNull String text, JavaParserUtil.@Nullable ParserWrapper parserWrapper) {
    return null;
  }


  @Override
  public void setUp(@NotNull AbstractBasicJavaParsingTestCase testCase) {
    JavaLanguage java = JavaLanguage.INSTANCE;

    LanguageASTFactory languageASTFactory = LanguageASTFactory.INSTANCE;
    testCase.addExplicit(languageASTFactory, java, new JavaASTFactory());
    testCase.clearCachesOfLanguageExtension(java, languageASTFactory);


    testCase.addExplicit(ElementTypeConverters.getInstance(), java, new JavaTestElementTypeConverterExtension());
    testCase.addExplicit(ElementTypeConverters.getInstance(), java, new JavaElementTypeConverterExtension());
    testCase.clearCachesOfLanguageExtension(java, ElementTypeConverters.getInstance());

    testCase.addExplicit(LanguageASTFactory.INSTANCE, java, new JavaASTFactory());
    testCase.clearCachesOfLanguageExtension(java, LanguageASTFactory.INSTANCE);

    ExtensionPointName<JavaLanguageLevelProvider> levelProviderExtensionPointName =
      new ExtensionPointName<>("com.intellij.java.syntax.languageLevelProvider");

    testCase.addExtensionPoint(levelProviderExtensionPointName, JavaLanguageLevelProvider.class);
    testCase.addExplicit(levelProviderExtensionPointName, new JavaLanguageLevelProvider() {

      @Override
      public @NotNull LanguageLevel getLanguageLevel(@NotNull SyntaxNode node) {
        return myLanguageLevel;
      }
    });
  }

  @Override
  public void configure(@NotNull PsiFile file) {
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
  }

  @Override
  public boolean checkPsi() {
    return true;
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel level) {
    myLanguageLevel = level;
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public @Nullable PsiFile createPsiFileForFullTestFile(AbstractBasicJavaParsingTestCase aCase,
                                                        @NotNull String name,
                                                        @NotNull String text) {
    return createPsiFile(aCase, name, text, (Consumer<PsiBuilder>)builder -> new JavaParser().getFileParser().parse(builder));
  }

  private static PsiBuilder createBuilder(ASTNode chameleon) {
    PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    builder.setDebugMode(true);
    return builder;
  }

  private static class MyIFileElementType extends IFileElementType {
    private final Consumer<PsiBuilder> myParser;

    MyIFileElementType(String name, Consumer<PsiBuilder> parser) {
      super(name, JavaLanguage.INSTANCE);
      myParser = parser;
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon);

      PsiBuilder.Marker root = builder.mark();
      myParser.accept(builder);
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
