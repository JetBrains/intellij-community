/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.*;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.FileParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.parsing.FileTextParsing;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaFileElementType extends ILightStubFileElementType<PsiJavaFileStub> {
  public static boolean USE_NEW_STUB_BUILDER = true & JavaParserDefinition.USE_NEW_PARSER;

  public static final int STUB_VERSION = JavaParserDefinition.USE_NEW_PARSER ? USE_NEW_STUB_BUILDER ? 6 : 5 : 4;

  public JavaFileElementType() {
    super("java.FILE", StdLanguages.JAVA);
  }

  @Override
  public StubBuilder getBuilder() {
    return USE_NEW_STUB_BUILDER ? new JavaLightStubBuilder() : new JavaFileStubBuilder();
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
  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(final ASTNode chameleon) {
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getLightTree();
  }

  @Override
  public ASTNode parseContents(final ASTNode chameleon) {
    if (JavaParserDefinition.USE_NEW_PARSER) {
      final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
      doParse(builder);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    FileElement node = (FileElement)chameleon;
    final CharSequence seq = node.getChars();

    final PsiManager manager = node.getManager();
    final JavaLexer lexer = new JavaLexer(PsiUtil.getLanguageLevel(node.getPsi()));
    return FileTextParsing.parseFileText(manager, lexer, seq, 0, seq.length(), node.getCharTable());
  }

  private void doParse(final PsiBuilder builder) {
    final PsiBuilder.Marker root = builder.mark();
    FileParser.parse(builder);
    root.done(this);
  }

  @Override
  public String getExternalId() {
    return "java.FILE";
  }

  @Override
  public void serialize(final PsiJavaFileStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    dataStream.writeName(stub.getPackageName());
  }

  @Override
  public PsiJavaFileStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    StringRef packName = dataStream.readName();
    return new PsiJavaFileStubImpl(null, packName, compiled);
  }

  @Override
  public void indexStub(final PsiJavaFileStub stub, final IndexSink sink) {
  }
}
