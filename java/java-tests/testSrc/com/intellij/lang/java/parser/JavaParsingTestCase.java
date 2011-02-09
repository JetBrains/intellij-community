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
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
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
    return JavaPsiFacadeEx.getInstanceEx(getProject());
  }

  protected static void withLevel(final LanguageLevel level, final Runnable r) {
    final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtension.getInstance(getModule());

    final LanguageLevel projectLevel = projectExt.getLanguageLevel();
    final LanguageLevel moduleLevel = moduleExt.getLanguageLevel();
    try {
      projectExt.setLanguageLevel(level);

      final LanguageLevelModuleExtension modifiable = (LanguageLevelModuleExtension)moduleExt.getModifiableModel(true);
      modifiable.setLanguageLevel(level);
      modifiable.commit();

      r.run();
    }
    finally {
      final LanguageLevelModuleExtension modifiable = (LanguageLevelModuleExtension)moduleExt.getModifiableModel(true);
      modifiable.setLanguageLevel(moduleLevel);
      modifiable.commit();

      projectExt.setLanguageLevel(projectLevel);
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
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    builder.setDebugMode(true);
    return builder;
  }
}
