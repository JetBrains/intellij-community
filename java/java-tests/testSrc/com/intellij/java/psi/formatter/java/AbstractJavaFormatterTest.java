// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.formatting.FormatterTestUtils.Action;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.formatting.FormatterTestUtils.ACTIONS;
import static com.intellij.formatting.FormatterTestUtils.Action.REFORMAT;

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 */
public abstract class AbstractJavaFormatterTest extends LightIdeaTestCase {

  @NotNull
  public static String shiftIndentInside(@NotNull String initial, final int i, boolean shiftEmptyLines) {
    StringBuilder result = new StringBuilder(initial.length());
    List<byte[]> lines;
    try {
      LineReader reader = new LineReader(new ByteArrayInputStream(initial.getBytes(StandardCharsets.UTF_8)));
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
        result.append(new String(line, StandardCharsets.UTF_8));
      }
      finally {
        first = false;
      }
    }

    return result.toString();
  }


  public JavaCodeStyleSettings getJavaSettings() {
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

  public CommonCodeStyleSettings getSettings() {
    CodeStyleSettings rootSettings = CodeStyle.getSettings(getProject());
    return rootSettings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  public CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE);
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
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.JDK_15_PREVIEW);
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

  public void formatEveryoneAndCheckIfResultEqual(final String @NotNull ... before) {
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
    }), action == REFORMAT ? ReformatCodeProcessor.getCommandName() : "", "");

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
