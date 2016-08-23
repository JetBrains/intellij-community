/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaFileElementType extends ILightStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 36;

  public JavaFileElementType() {
    super("java.FILE", JavaLanguage.INSTANCE);
  }

  @Override
  public LightStubBuilder getBuilder() {
    return new JavaLightStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION;
  }

  @Override
  public boolean shouldBuildStubFor(final VirtualFile file) {
    return isInSourceContent(file);
  }

  public static boolean isInSourceContent(@NotNull VirtualFile file) {
    final VirtualFile dir = file.getParent();
    return dir == null || dir.getUserData(LanguageLevel.KEY) != null;
  }

  @Override
  public ASTNode createNode(final CharSequence text) {
    return new JavaFileElement(text);
  }

  @Override
  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(final ASTNode chameleon) {
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getLightTree();
  }

  @Override
  public ASTNode parseContents(final ASTNode chameleon) {
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getTreeBuilt().getFirstChildNode();
  }

  private void doParse(final PsiBuilder builder) {
    final PsiBuilder.Marker root = builder.mark();
    JavaParser.INSTANCE.getFileParser().parse(builder);
    root.done(this);
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "java.FILE";
  }

  @Override
  public void serialize(@NotNull PsiJavaFileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    LanguageLevel level = stub.getLanguageLevel();
    dataStream.writeByte(level != null ? level.ordinal() : -1);
    dataStream.writeName(stub.getPackageName());
  }

  @NotNull
  @Override
  public PsiJavaFileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    int level = dataStream.readByte();
    StringRef packageName = dataStream.readName();
    return new PsiJavaFileStubImpl(null, StringRef.toString(packageName), level >= 0 ? LanguageLevel.values()[level] : null, compiled);
  }

  @Override
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public void indexStub(@NotNull PsiJavaFileStub stub, @NotNull IndexSink sink) { }
}
