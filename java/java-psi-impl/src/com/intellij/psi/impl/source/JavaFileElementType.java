// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.psi.ParsingDiagnostics;
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
    return ApplicationManager.getApplication().getService(InternalPersistentJavaLanguageLevelReaderService.class)
             .getPersistedLanguageLevel(file) != null;
  }

  @Override
  public ASTNode createNode(CharSequence text) {
    return new JavaFileElement(text);
  }

  @Override
  public ASTNode parseContents(@NotNull ASTNode chameleon) {
    PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    long startTime = System.nanoTime();
    doParse(builder);
    ASTNode result = builder.getTreeBuilt().getFirstChildNode();
    ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
    return result;
  }

  @ApiStatus.Internal
  public static void doParse(PsiBuilder builder) {
    PsiBuilder.Marker root = builder.mark();
    JavaParser.INSTANCE.getFileParser().parse(builder);
    root.done(JavaParserDefinition.JAVA_FILE);
  }
}
