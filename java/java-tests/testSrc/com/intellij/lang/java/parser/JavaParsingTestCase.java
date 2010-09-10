/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;


public abstract class JavaParsingTestCase extends ParsingTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public JavaParsingTestCase(@NonNls final String dataPath) {
    super(dataPath, "java");
    IdeaTestCase.initPlatformPrefix();
  }

  public static JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(ourProject);
  }

  protected static void withLevel(final LanguageLevel level, final Runnable r) {
    final LanguageLevel current = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level);
      r.run();
    }
    finally {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(current);
    }
  }

  protected interface TestParser {
    void parse(PsiBuilder builder);
  }

  protected void doParserTest(final String text, final TestParser parser) {
    final String name = getTestName(false);
    myFile = createPsiFile(name, text, parser);
    try {
      checkResult(name + ".txt", myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static IFileElementType TEST_FILE_ELEMENT_TYPE = null;
  private static TestParser TEST_PARSER;

  protected PsiFile createPsiFile(final String name, final String text, final TestParser parser) {
    if (TEST_FILE_ELEMENT_TYPE == null) {
      TEST_FILE_ELEMENT_TYPE = new IFileElementType("test.java.file", StdLanguages.JAVA) {
        @Override
        public ASTNode parseContents(final ASTNode chameleon) {
          final PsiBuilder builder = createBuilder(chameleon);

          final PsiBuilder.Marker root = builder.mark();
          TEST_PARSER.parse(builder);
          if (!builder.eof()) {
            final PsiBuilder.Marker unparsed = builder.mark();
            while (!builder.eof()) builder.advanceLexer();
            unparsed.error("Unparsed tokens");
          }
          root.done(this);

          final ASTNode rootNode = builder.getTreeBuilt();
          return rootNode.getFirstChildNode();
        }
      };
    }

    TEST_PARSER = parser;

    final LightVirtualFile virtualFile = new LightVirtualFile(name + '.' + myFileExt, StdFileTypes.JAVA, text, -1);
    final FileViewProvider viewProvider = new SingleRootFileViewProvider(PsiManager.getInstance(getProject()), virtualFile, true);
    return new PsiJavaFileImpl(viewProvider) {
      @Override
      protected FileElement createFileElement(final CharSequence text) {
        return new FileElement(TEST_FILE_ELEMENT_TYPE, text);
      }
    };
  }

  private static PsiBuilder createBuilder(final ASTNode chameleon) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : chameleon;
    final Project project = psi.getProject();
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final PsiBuilder builder = factory.createBuilder(project, chameleon);

    builder.setDebugMode(true);

    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    JavaParserUtil.setLanguageLevel(builder, level);

    return builder;
  }

  @Override
  protected void doTest(final boolean checkResult) {
    doTestDefaultParser(checkResult);
    doTestNewParser();
  }

  protected void doTestDefaultParser(final boolean checkResult) {
    super.doTest(checkResult);
  }

  // todo: drop after switching to new parser
  protected void doTestNewParser() {
    final String name = getTestName(false);
    final String text;
    try {
      text = loadFile(name + "." + myFileExt);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    doParserTest(text, new TestParser() {
      public void parse(final PsiBuilder builder) {
        FileParser.parse(builder);
      }
    });
  }
}
