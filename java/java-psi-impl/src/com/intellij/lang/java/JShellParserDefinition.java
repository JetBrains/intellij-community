// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.parser.JShellParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.JShellFileImpl;
import com.intellij.psi.impl.source.tree.IJShellElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellParserDefinition extends JavaParserDefinition {
  public static final IFileElementType FILE_ELEMENT_TYPE = new IFileElementType("JSHELL_FILE", JShellLanguage.INSTANCE);

  private static final PsiParser PARSER = new PsiParser() {
    @Override
    public @NotNull ASTNode parse(@NotNull IElementType rootElement, @NotNull PsiBuilder builder) {
      JavaParserUtil.setLanguageLevel(builder, LanguageLevel.HIGHEST);
      final PsiBuilder.Marker r = builder.mark();
      JShellParser.INSTANCE.getFileParser().parse(builder);
      r.done(rootElement);
      return builder.getTreeBuilt();
    }
  };

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new JShellFileImpl(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_ELEMENT_TYPE;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof IJShellElementType) {
      return ((IJShellElementType)type).createPsi(node);
    }
    return super.createElement(node);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return PARSER;
  }
}
