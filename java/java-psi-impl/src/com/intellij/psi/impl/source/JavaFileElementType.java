// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.element.JavaSyntaxElementType;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.PsiSyntaxBuilderWithLanguageLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.ParsingDiagnostics;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class JavaFileElementType extends IFileElementType {
  public static final int STUB_VERSION = 63;

  public JavaFileElementType() {
    super("java.FILE", JavaLanguage.INSTANCE);
  }

  public static boolean isInSourceContent(@NotNull VirtualFile file) {
    //RC: this is a bit hackish implementation: we rely on the fact that project's sources have languageLevel property
    //    pushed for them, so if this property is present for a file => this file is under 'source' tree
    return ApplicationManager.getApplication().getService(InternalPersistentJavaLanguageLevelReaderService.class)
             .getPersistedLanguageLevel(file) != null;
  }

  @Override
  public ASTNode createNode(CharSequence text) {
    return new JavaFileElement(text);
  }

  @Override
  public ASTNode parseContents(@NotNull ASTNode chameleon) {
    PsiSyntaxBuilderWithLanguageLevel builderAndLevel = JavaParserUtil.createSyntaxBuilder(chameleon);
    PsiSyntaxBuilder psiSyntaxBuilder = builderAndLevel.getBuilder();
    SyntaxTreeBuilder builder = psiSyntaxBuilder.getSyntaxTreeBuilder();
    long startTime = System.nanoTime();
    doParse(builder, builderAndLevel.getLanguageLevel());
    ASTNode result = psiSyntaxBuilder.getTreeBuilt().getFirstChildNode();
    ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
    return result;
  }

  @ApiStatus.Internal
  public static void doParse(@NotNull SyntaxTreeBuilder builder,
                             @NotNull LanguageLevel languageLevel) {
    SyntaxTreeBuilder.Marker root = builder.mark();
    new JavaParser(languageLevel).getFileParser().parse(builder);
    root.done(JavaSyntaxElementType.JAVA_FILE);
  }
}
