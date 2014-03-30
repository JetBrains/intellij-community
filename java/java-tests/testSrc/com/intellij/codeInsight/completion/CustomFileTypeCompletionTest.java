package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/customFileType/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testKeyWordCompletion() throws Exception {
    configureByFile(BASE_PATH + "1.cs");
    checkResultByFile(BASE_PATH + "1_after.cs");

    configureByFile(BASE_PATH + "2.cs");
    checkResultByFile(BASE_PATH + "2_after.cs");
  }

  public void testWordCompletion() throws Throwable {
    configureByFile(BASE_PATH + "WordCompletion.cs");
    testByCount(2, "while", "whiwhiwhi");
  }

  public void testErlang() throws Throwable {
    configureByFile(BASE_PATH + "Erlang.erl");
    testByCount(2, "case", "catch");
  }

  public void testComment() throws Throwable {
    configureByFile(BASE_PATH + "foo.cs");
    testByCount(0, new String[] { null });
  }

  public void testPlainTextSubstitution() throws IOException {
    FileTypeManagerEx.getInstanceEx().registerFileType(MockLanguageFileType.INSTANCE, "xxx");
    try {
      configureFromFileText("a.xxx", "aaa a<caret>");
      complete();
      checkResultByText("aaa aaa<caret>");
    }
    finally {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(MockLanguageFileType.INSTANCE);

    }
  }

}
