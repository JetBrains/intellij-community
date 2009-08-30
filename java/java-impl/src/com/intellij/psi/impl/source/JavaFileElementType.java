/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
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
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaFileElementType extends IStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 2;

  public JavaFileElementType() {
    super("java.FILE", StdLanguages.JAVA);
  }

  public StubBuilder getBuilder() {
    return new JavaFileStubBuilder();
  }

  public int getStubVersion() {
    return STUB_VERSION;
  }

  @Override
  public boolean shouldBuildStubFor(final VirtualFile file) {
    final VirtualFile dir = file.getParent();
    return dir == null || dir.getUserData(LanguageLevel.KEY) != null;
  }

  public ASTNode parseContents(ASTNode chameleon) {
    FileElement node = (FileElement)chameleon;
    final CharSequence seq = node.getChars();

    final PsiManager manager = node.getManager();
    final JavaLexer lexer = new JavaLexer(PsiUtil.getLanguageLevel(node.getPsi()));
    return FileTextParsing.parseFileText(manager, lexer, seq, 0, seq.length(), node.getCharTable());
  }

  public String getExternalId() {
    return "java.FILE";
  }

  public void serialize(final PsiJavaFileStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    dataStream.writeName(stub.getPackageName());
  }

  public PsiJavaFileStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    StringRef packName = dataStream.readName();
    return new PsiJavaFileStubImpl(packName, compiled);
  }

  public void indexStub(final PsiJavaFileStub stub, final IndexSink sink) {
  }
}
