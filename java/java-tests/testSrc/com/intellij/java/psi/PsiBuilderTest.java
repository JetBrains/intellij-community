// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LocalTimeCounter;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class PsiBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  private PsiBuilderImpl myBuilder;

  @Test
  public void testEmptyProgram() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder("");
      final PsiBuilder.Marker fileMarker = myBuilder.mark();
      fileMarker.done(JavaParserDefinition.JAVA_FILE);
      ASTNode fileNode = myBuilder.getTreeBuilt();
      assertNotNull(fileNode);
      assertEquals("", fileNode.getText());
    });
  }

  @Test
  public void testProgramWithSingleKeyword() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder("package");

      final PsiBuilder.Marker fileMarker = myBuilder.mark();
      assertEquals("package", myBuilder.getTokenText());
      assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
      final PsiBuilder.Marker packageStatementMarker = myBuilder.mark();
      myBuilder.advanceLexer();
      assertTrue(myBuilder.eof());
      packageStatementMarker.done(JavaElementType.PACKAGE_STATEMENT);
      fileMarker.done(JavaParserDefinition.JAVA_FILE);

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
    });
  }

  private static PsiBuilderImpl createBuilder(final String text) {
    return createBuilder(text,null);
  }
  private static PsiBuilderImpl createBuilder(final String text, ASTNode originalTree) {
    final Language lang = JavaFileType.INSTANCE.getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    assertNotNull(parserDefinition);
    Project project = projectFixture.get().getProject();
    String fileName = "x.java";
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false);
    return new PsiBuilderImpl(project, psiFile, parserDefinition, JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5),
                              SharedImplUtil.findCharTableByTree(psiFile.getNode()), text, originalTree, null);
  }

  @Test
  public void testTrailingWhitespaces() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder("foo\n\nx");
      final PsiBuilder.Marker marker = myBuilder.mark();
      while (!myBuilder.eof()) {
        myBuilder.advanceLexer();
      }
      marker.done(JavaParserDefinition.JAVA_FILE);
      assertEquals("foo\n\nx", myBuilder.getTreeBuilt().getText());
    });
  }

  @Test
  public void testRollback() {
    ReadAction.runBlocking(() -> {
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
      fileMarker.done(JavaParserDefinition.JAVA_FILE);

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
    });
  }

  @Test
  public void testDrop() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder("package");

      final PsiBuilder.Marker fileMarker = myBuilder.mark();
      assertEquals("package", myBuilder.getTokenText());
      assertEquals(JavaTokenType.PACKAGE_KEYWORD, myBuilder.getTokenType());
      final PsiBuilder.Marker packageStatementMarker = myBuilder.mark();
      myBuilder.advanceLexer();
      assertTrue(myBuilder.eof());
      packageStatementMarker.drop();
      fileMarker.done(JavaParserDefinition.JAVA_FILE);

      ASTNode fileNode = myBuilder.getTreeBuilt();
      assertNotNull(fileNode);
      assertEquals("package", fileNode.getText());
      assertSame(fileNode.getFirstChildNode(), fileNode.getLastChildNode());

      ASTNode leaf = fileNode.getFirstChildNode();
      assertNotNull(leaf);
      assertEquals(JavaTokenType.PACKAGE_KEYWORD, leaf.getElementType());
      assertEquals("package", leaf.getText());
      assertNull(leaf.getFirstChildNode());
    });
  }

  @Test
  public void testAdvanceBeyondEof() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder("package");
      for(int i=0; i<20; i++) {
        myBuilder.eof();
        myBuilder.advanceLexer();
      }
      assertTrue(myBuilder.eof());
    });
  }

  @Test
  public void testAssertionFailureOnUnbalancedMarkers() throws Exception {
    ReadAction.runBlocking(() -> {
      TestLoggerKt.rethrowLoggedErrorsIn(() -> {
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
      });
    });
  }

  @Test
  public void testNotAllTokensProcessed() throws Exception {
    ReadAction.runBlocking(() -> {
      TestLoggerKt.rethrowLoggedErrorsIn(() -> {
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
      });
    });
  }

  @Test
  public void testMergeWhenEmptyElementAfterWhitespaceIsLastChild() {
    ReadAction.runBlocking(() -> {
      myBuilder = createBuilder(" foo bar");
      parseWhenEmptyElementAfterWhitespaceIsLastChild();
      final ASTNode tree = myBuilder.getTreeBuilt();
      new DummyHolder(PsiManager.getInstance(projectFixture.get().getProject()), (TreeElement)tree, null);

      myBuilder = createBuilder("  bar", tree);
      parseWhenEmptyElementAfterWhitespaceIsLastChild();
      DebugUtil.performPsiModification(null, () -> {
        try {
          myBuilder.getTreeBuilt();
          fail();
        }
        catch (BlockSupport.ReparsedSuccessfullyException e) {
          e.getDiffLog().performActualPsiChange(tree.getPsi().getContainingFile());
        }
      });

      assertEquals("  bar", tree.getText());
    });
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
