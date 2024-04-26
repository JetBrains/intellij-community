// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.ParsingDiagnostics;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaFileElementType extends ILightStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 59;

  private static volatile int TEST_STUB_VERSION_MODIFIER = 0;

  public JavaFileElementType() {
    super("java.FILE", JavaLanguage.INSTANCE);
  }

  @Override
  public LightStubBuilder getBuilder() {
    return new JavaLightStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION + (ApplicationManager.getApplication().isUnitTestMode() ? TEST_STUB_VERSION_MODIFIER : 0);
  }

  @Override
  public boolean shouldBuildStubFor(VirtualFile file) {
    return isInSourceContent(file);
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
  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(ASTNode chameleon) {
    PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getLightTree();
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

  private void doParse(PsiBuilder builder) {
    PsiBuilder.Marker root = builder.mark();
    JavaParser.INSTANCE.getFileParser().parse(builder);
    root.done(this);
  }

  @Override
  public @NotNull String getExternalId() {
    return "java.FILE";
  }

  @Override
  public void serialize(@NotNull PsiJavaFileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    LanguageLevel level = stub.getLanguageLevel();
    dataStream.writeByte(level != null ? level.ordinal() : -1);
    dataStream.writeName(stub.getPackageName());
  }

  @Override
  public @NotNull PsiJavaFileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    int level = dataStream.readByte();
    String packageName = dataStream.readNameString();
    return new PsiJavaFileStubImpl(null, packageName, level >= 0 ? LanguageLevel.values()[level] : null, compiled);
  }

  @Override
  public void indexStub(@NotNull PsiJavaFileStub stub, @NotNull IndexSink sink) { }

  @ApiStatus.Internal
  public static void setTestStubVersionModifier(int modifier) {
    TEST_STUB_VERSION_MODIFIER = modifier;
  }
}
