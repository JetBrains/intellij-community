// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.PsiSyntaxBuilderWithLanguageLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.ParsingDiagnostics;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.psi.stubs.LightLanguageStubDefinition;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class JavaStubDefinition implements LightLanguageStubDefinition {
  private static volatile int TEST_STUB_VERSION_MODIFIER = 0;

  @Override
  public @NotNull LightStubBuilder getBuilder() {
    return new JavaLightStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return JavaFileElementType.STUB_VERSION + (ApplicationManager.getApplication().isUnitTestMode() ? TEST_STUB_VERSION_MODIFIER : 0);
  }

  @Override
  public boolean shouldBuildStubFor(@NotNull VirtualFile file) {
    return shouldBuildStubForFile(file);
  }

  public static boolean shouldBuildStubForFile(@NotNull VirtualFile file) {
    return JavaFileElementType.isInSourceContent(file);
  }

  @Override
  public @NotNull FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(@NotNull ASTNode chameleon) {
    PsiSyntaxBuilderWithLanguageLevel builderAndLevel = JavaParserUtil.createSyntaxBuilder(chameleon);
    PsiSyntaxBuilder psiSyntaxBuilder = builderAndLevel.getBuilder();
    SyntaxTreeBuilder builder = psiSyntaxBuilder.getSyntaxTreeBuilder();
    long startTime = System.nanoTime();
    JavaFileElementType.doParse(builder, builderAndLevel.getLanguageLevel());
    FlyweightCapableTreeStructure<LighterASTNode> tree = psiSyntaxBuilder.getLightTree();
    ParsingDiagnostics.registerParse(builder, JavaLanguage.INSTANCE, System.nanoTime() - startTime);
    return tree;
  }

  @ApiStatus.Internal
  public static void setTestStubVersionModifier(int modifier) {
    TEST_STUB_VERSION_MODIFIER = modifier;
  }
}
