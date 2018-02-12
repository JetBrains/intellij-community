/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.LightIdeaTestCase;
import junit.framework.AssertionFailedError;

/**
 * @since Jan 21, 2005
 * @author max
 */
public class PsiBuilderTest extends LightIdeaTestCase {
  private PsiBuilderImpl myBuilder;

  @Override
  protected void tearDown() throws Exception {
    myBuilder = null;
    super.tearDown();
  }

  public void testEmptyProgram() {
    myBuilder = createBuilder("");
    final PsiBuilder.Marker fileMarker = myBuilder.mark();
    fileMarker.done(JavaStubElementTypes.JAVA_FILE);
    ASTNode fileNode = myBuilder.getTreeBuilt();
    assertNotNull(fileNode);
    assertEquals("", fileNode.getText());
  }

  public void testProgramWithSingleKeyword() {
    myBuilder = createBuilder("package");

    final PsiBuilder.Marker fileMarker = myBuilder.mark();
    assertEquals("package", myBuilder.getTokenText());
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
    final PsiBuilder.Marker packageStatementMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    assertTrue(myBuilder.eof());
    packageStatementMarker.done(JavaElementType.PACKAGE_STATEMENT);
    fileMarker.done(JavaStubElementTypes.JAVA_FILE);

    ASTNode fileNode = myBuilder.getTreeBuilt();
    assertNotNull(fileNode);
    assertEquals("package", fileNode.getText());
    assertSame(fileNode.getFirstChildNode(), fileNode.getLastChildNode());
    ASTNode packageNode = fileNode.getFirstChildNode();
    assertNotNull(packageNode);
    assertEquals("package", packageNode.getText());
    assertEquals(JavaElementType.PACKAGE_STATEMENT, packageNode.getElementType());

    ASTNode leaf = packageNode.getFirstChildNode();
    assertNotNull(leaf);
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, leaf.getElementType());
  }

  private static PsiBuilderImpl createBuilder(final String text) {
    return createBuilder(text,null);
  }
  private static PsiBuilderImpl createBuilder(final String text, ASTNode originalTree) {
    final Language lang = StdFileTypes.JAVA.getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    assertNotNull(parserDefinition);
    PsiFile psiFile = createFile("x.java", text);
    return new PsiBuilderImpl(getProject(), psiFile, parserDefinition, JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5),
                              SharedImplUtil.findCharTableByTree(psiFile.getNode()), text, originalTree, null);
  }

  public void testTrailingWhitespaces() {
    myBuilder = createBuilder("foo\n\nx");
    final PsiBuilder.Marker marker = myBuilder.mark();
    while (!myBuilder.eof()) {
      myBuilder.advanceLexer();
    }
    marker.done(JavaStubElementTypes.JAVA_FILE);
    assertEquals("foo\n\nx", myBuilder.getTreeBuilt().getText());
  }

  public void testRollback() {
    myBuilder = createBuilder("package");

    PsiBuilder.Marker fileMarker = myBuilder.mark();
    assertEquals("package", myBuilder.getTokenText());
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
    PsiBuilder.Marker packageStatementMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    assertTrue(myBuilder.eof());
    packageStatementMarker.done(JavaElementType.PACKAGE_STATEMENT);

    fileMarker.rollbackTo();

    fileMarker = myBuilder.mark();
    assertEquals("package", myBuilder.getTokenText());
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
    packageStatementMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    assertTrue(myBuilder.eof());
    packageStatementMarker.done(JavaElementType.PACKAGE_STATEMENT);
    fileMarker.done(JavaStubElementTypes.JAVA_FILE);

    ASTNode fileNode = myBuilder.getTreeBuilt();
    assertNotNull(fileNode);
    assertEquals("package", fileNode.getText());
    assertSame(fileNode.getFirstChildNode(), fileNode.getLastChildNode());
    ASTNode packageNode = fileNode.getFirstChildNode();
    assertNotNull(packageNode);
    assertEquals("package", packageNode.getText());
    assertEquals(JavaElementType.PACKAGE_STATEMENT, packageNode.getElementType());

    ASTNode leaf = packageNode.getFirstChildNode();
    assertNotNull(leaf);
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, leaf.getElementType());
  }

  public void testDrop() {
    myBuilder = createBuilder("package");

    final PsiBuilder.Marker fileMarker = myBuilder.mark();
    assertEquals("package", myBuilder.getTokenText());
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
    final PsiBuilder.Marker packageStatementMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    assertTrue(myBuilder.eof());
    packageStatementMarker.drop();
    fileMarker.done(JavaStubElementTypes.JAVA_FILE);

    ASTNode fileNode = myBuilder.getTreeBuilt();
    assertNotNull(fileNode);
    assertEquals("package", fileNode.getText());
    assertSame(fileNode.getFirstChildNode(), fileNode.getLastChildNode());

    ASTNode leaf = fileNode.getFirstChildNode();
    assertNotNull(leaf);
    assertEquals(JavaTokenType.PACKAGE_KEYWORD, leaf.getElementType());
    assertEquals("package", leaf.getText());
    assertNull(leaf.getFirstChildNode());
  }

  public void testAdvanceBeyondEof() {
    myBuilder = createBuilder("package");
    for(int i=0; i<20; i++) {
      myBuilder.eof();
      myBuilder.advanceLexer();
    }
    assertTrue(myBuilder.eof());
  }

  public void testAssertionFailureOnUnbalancedMarkers() {
    myBuilder = createBuilder("foo");
    myBuilder.setDebugMode(true);
    PsiBuilder.Marker m = myBuilder.mark();
    @SuppressWarnings("UnusedDeclaration") PsiBuilder.Marker m1 = myBuilder.mark();
    myBuilder.getTokenType();
    myBuilder.advanceLexer();
    try {
      m.done(JavaTokenType.PACKAGE_KEYWORD);
      fail("Assertion must fire");
    }
    catch (AssertionFailedError e) {
      throw e;
    }
    catch (Throwable e) {
      if (!e.getMessage().startsWith("Another not done marker")) {
        fail("Wrong assertion message");
      }
    }
  }

  public void testNotAllTokensProcessed() {
    myBuilder = createBuilder("foo");
    myBuilder.setDebugMode(true);
    final PsiBuilder.Marker m = myBuilder.mark();
    m.done(JavaTokenType.PACKAGE_KEYWORD);
    try {
      myBuilder.getTreeBuilt();
      fail("Assertion must fire");
    }
    catch (AssertionFailedError e) {
      throw e;
    }
    catch (Throwable e) {
      if (!e.getMessage().startsWith("Tokens [IDENTIFIER] were not inserted into the tree")) {
        fail("Wrong assertion message");
      }
    }
  }

  public void testMergeWhenEmptyElementAfterWhitespaceIsLastChild() {
    myBuilder = createBuilder(" foo bar");
    parseWhenEmptyElementAfterWhitespaceIsLastChild();
    final ASTNode tree = myBuilder.getTreeBuilt();
    new DummyHolder(getPsiManager(), (TreeElement)tree, null);

    myBuilder = createBuilder("  bar", tree);
    parseWhenEmptyElementAfterWhitespaceIsLastChild();
    DebugUtil.startPsiModification(null);
    try {
      myBuilder.getTreeBuilt();
      fail();
    }
    catch (BlockSupport.ReparsedSuccessfullyException e) {
      e.getDiffLog().performActualPsiChange(tree.getPsi().getContainingFile());
    }
    finally {
      DebugUtil.finishPsiModification();
    }

    assertEquals("  bar", tree.getText());
  }

  private void parseWhenEmptyElementAfterWhitespaceIsLastChild() {
    final PsiBuilder.Marker root = myBuilder.mark();

    final PsiBuilder.Marker composite = myBuilder.mark();
    final PsiBuilder.Marker backup = myBuilder.mark();
    if ("foo".equals(myBuilder.getTokenText())) {
      myBuilder.advanceLexer();
      myBuilder.getTokenType();
      myBuilder.mark().done(JavaStubElementTypes.TYPE_PARAMETER_LIST);
      backup.done(JavaStubElementTypes.ANNOTATION_METHOD);
    } else {
      backup.rollbackTo();
    }
    composite.done(JavaStubElementTypes.ANONYMOUS_CLASS);

    myBuilder.getTokenType();
    myBuilder.advanceLexer();
    root.done(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }
}
