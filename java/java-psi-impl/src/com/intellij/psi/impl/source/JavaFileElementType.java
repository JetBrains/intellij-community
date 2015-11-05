/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.hierarchy.JavaStubIndexer;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaFileElementType extends ILightStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 22;

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
  public void serialize(@NotNull final PsiJavaFileStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    dataStream.writeName(stub.getPackageName());
  }

  @NotNull
  @Override
  public PsiJavaFileStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    StringRef packName = dataStream.readName();
    return new PsiJavaFileStubImpl(null, packName, compiled);
  }

  @Override
  public void indexStub(@NotNull final PsiJavaFileStub stub, @NotNull final IndexSink sink) {
    Integer fileId = stub.getUserData(IndexingDataKeys.VIRTUAL_FILE_ID);
    if (fileId == null) return;
    IndexTree.Unit unit = JavaStubIndexer.translate(fileId, stub);
    if (unit != null) {
      sink.occurrence(JavaStubIndexKeys.UNITS, unit);
    }
  }
}
