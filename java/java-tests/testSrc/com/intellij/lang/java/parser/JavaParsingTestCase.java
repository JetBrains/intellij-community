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
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockModule;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaASTFactory;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;


public abstract class JavaParsingTestCase extends ParsingTestCase {

  private Module myModule;
  private LanguageLevel myLanguageLevel;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public JavaParsingTestCase(@NonNls final String dataPath) {
    super("psi/"+dataPath, "java", new JavaParserDefinition());
    IdeaTestCase.initPlatformPrefix();
  }

  protected void withLevel(final LanguageLevel level, final Runnable r) {
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = level;
    try {
      r.run();
    }
    finally {
      myLanguageLevel = prev;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModule = new MockModule(getProject(), getTestRootDisposable());
    myLanguageLevel = LanguageLevel.JDK_1_6;
    getProject().registerService(LanguageLevelProjectExtension.class, new LanguageLevelProjectExtensionImpl(getProject()));
    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new JavaASTFactory());
    try {
      registerApplicationService((Class<Object>)Class.forName("com.intellij.psi.jsp.JspSpiUtil"),
                                 Class.forName("com.intellij.jsp.impl.JspSpiUtilImpl").newInstance());
    }
    catch (Exception ex) {
      // jsp not available
    }
  }

  @Override
  protected PsiFile createFile(String name, String text) {
    final PsiFile file = super.createFile(name, text);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    return file;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myModule = null;
  }

  protected interface TestParser {
    void parse(PsiBuilder builder);
  }

  public Module getModule() {
    return myModule;
  }


  protected void doParserTest(final String text, final TestParser parser) {
    final String name = getTestName(false);
    myFile = createPsiFile(name, text, parser);
    myFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    try {
      checkResult(name, myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static IFileElementType TEST_FILE_ELEMENT_TYPE = null;
  private static TestParser TEST_PARSER;

  private PsiFile createPsiFile(final String name, final String text, final TestParser parser) {
    if (TEST_FILE_ELEMENT_TYPE == null) {
      TEST_FILE_ELEMENT_TYPE = new MyIFileElementType();
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

  private static class MyIFileElementType extends IFileElementType {
    public MyIFileElementType() {
      super("test.java.file", JavaLanguage.INSTANCE);
    }

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
  }
}
