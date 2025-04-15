// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.java.syntax.JavaSyntaxDefinition;
import com.intellij.java.syntax.parser.JShellParser;
import com.intellij.lang.*;
import com.intellij.platform.syntax.psi.ParsingDiagnostics;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

class JShellFileElementType extends IFileElementType {
  JShellFileElementType() {
    super("JSHELL_FILE", JShellLanguage.INSTANCE);
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    Language languageForParser = JShellLanguage.INSTANCE;
    LanguageLevel languageLevel = LanguageLevel.HIGHEST;
    JShellParser jShellParser = new JShellParser(languageLevel);
    PsiSyntaxBuilder builder = PsiSyntaxBuilderFactory.getInstance().createBuilder(chameleon,
                                                                                   JavaSyntaxDefinition.createLexer(languageLevel),
                                                                                   JShellLanguage.INSTANCE,
                                                                                   chameleon.getChars());
    long startTime = System.nanoTime();
    jShellParser.parse(builder.getSyntaxTreeBuilder());
    ASTNode node = builder.getTreeBuilt();
    ParsingDiagnostics.registerParse(builder, languageForParser, System.nanoTime() - startTime);
    return node.getFirstChildNode();
  }
}
