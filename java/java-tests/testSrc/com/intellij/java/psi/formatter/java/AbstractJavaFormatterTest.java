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
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.formatting.FormatterTestUtils.Action;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.formatting.FormatterTestUtils.ACTIONS;
import static com.intellij.formatting.FormatterTestUtils.Action.REFORMAT;

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */
public abstract class AbstractJavaFormatterTest extends LightIdeaTestCase {
  private JavaCodeStyleBean myCodeStyleBean;

  @NotNull
  public static String shiftIndentInside(@NotNull String initial, final int i, boolean shiftEmptyLines) {
    StringBuilder result = new StringBuilder(initial.length());
    List<byte[]> lines;
    try {
      LineReader reader = new LineReader(new ByteArrayInputStream(initial.getBytes(CharsetToolkit.UTF8_CHARSET)));
      lines = reader.readLines();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    boolean first = true;
    for (byte[] line : lines) {
      try {
        if (!first) result.append('\n');
        if (line.length > 0 || shiftEmptyLines) {
          StringUtil.repeatSymbol(result, ' ', i);
        }
        result.append(new String(line, CharsetToolkit.UTF8_CHARSET));
      }
      finally {
        first = false;
      }
    }

    return result.toString();
  }

  @NotNull
  public JavaCodeStyleBean getCodeStyleBean() {
    if (myCodeStyleBean == null) {
      myCodeStyleBean = new JavaCodeStyleBean();
      myCodeStyleBean.setRootSettings(CodeStyle.getSettings(getProject()));
    }
    return myCodeStyleBean;
  }

  public static JavaCodeStyleSettings getJavaSettings() {
    return getSettings().getRootSettings().getCustomSettings(JavaCodeStyleSettings.class);
  }
  
  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/java";

  public TextRange myTextRange;
  public TextRange myLineRange;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
  }

  public static CommonCodeStyleSettings getSettings() {
    CodeStyleSettings rootSettings = CodeStyle.getSettings(getProject());
    return rootSettings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  public static CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA);
  }

  public void doTest() {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }

  public void doTest(@NotNull String fileNameBefore, @NotNull String fileNameAfter) {
    doTextTest(REFORMAT, loadFile(fileNameBefore), loadFile(fileNameAfter));
  }

  public void doTestWithDetectableIndentOptions(@NotNull String text, @NotNull String textAfter) {
    DetectableIndentOptionsProvider provider = DetectableIndentOptionsProvider.getInstance();
    assertNotNull("DetectableIndentOptionsProvider not found", provider);
    provider.setEnabledInTest(true);
    try {
      doTextTest(text, textAfter);
    }
    finally {
      provider.setEnabledInTest(false);
    }
  }

  public void doTextTest(@NotNull  String text, @NotNull String textAfter) throws IncorrectOperationException {
    doTextTest(REFORMAT, text, textAfter);
  }

  public void doTextTest(@NotNull Action action, @NotNull String text, @NotNull String textAfter) throws IncorrectOperationException {
    final PsiFile file = createFile("A.java", text);
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);
    if (document == null) {
      fail("Document is null");
      return;
    }
    replaceAndProcessDocument(action, text, file, document);
    assertEquals(textAfter, document.getText());
    manager.commitDocument(document);
    assertEquals(textAfter, file.getText());
  }

  public void formatEveryoneAndCheckIfResultEqual(@NotNull final String...before) {
    assert before.length > 1;
    final PsiFile file = createFile("A.java", "");
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);
    String afterFirst = replaceAndProcessDocument(REFORMAT, before[0], file, document);
    for (String nextBefore: before) {
      assertEquals(afterFirst, replaceAndProcessDocument(REFORMAT, nextBefore, file, document));
    }
  }

  @NotNull
  private String replaceAndProcessDocument(@NotNull final Action action,
                                           @NotNull final String text,
                                           @NotNull final PsiFile file,
                                           @Nullable final Document document) throws IncorrectOperationException
  {
    if (document == null) {
      fail("Don't expect the document to be null");
      return null;
    }
    if (myLineRange != null) {
      final DocumentImpl doc = new DocumentImpl(text);
      myTextRange =
        new TextRange(doc.getLineStartOffset(myLineRange.getStartOffset()), doc.getLineEndOffset(myLineRange.getEndOffset()));
    }
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(0, document.getTextLength(), text);
      manager.commitDocument(document);
      try {
        TextRange rangeToUse = myTextRange;
        if (rangeToUse == null) {
          rangeToUse = file.getTextRange();
        }
        ACTIONS.get(action).run(file, rangeToUse.getStartOffset(), rangeToUse.getEndOffset());
      }
      catch (IncorrectOperationException e) {
        assertTrue(e.getLocalizedMessage(), false);
      }
    }), action == REFORMAT ? ReformatCodeProcessor.COMMAND_NAME : "", "");

    return document.getText();
  }

  public void doMethodTest(@NotNull String before, @NotNull String after) {
    doTextTest(
      REFORMAT,
      "class Foo{\n" + "    void foo() {\n" + before + '\n' + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + shiftIndentInside(after, 8, false) + '\n' + "    }\n" + "}"
    );
  }

  public void doClassTest(@NotNull String before, @NotNull String after) {
    doTextTest(
      REFORMAT,
      "class Foo{\n" + before + '\n' + "}",
      "class Foo {\n" + shiftIndentInside(after, 4, false) + '\n' + "}"
    );
  }

  protected static String loadFile(String name) {
    String fullName = BASE_PATH + File.separatorChar + name;
    try {
      String text = FileUtil.loadFile(new File(fullName));
      return StringUtil.convertLineSeparators(text);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
