package com.intellij.codeInsight.completion;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.sun.tools.javac.Main;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public abstract class AbstractCompilerAwareTest extends JavaCodeInsightFixtureTestCase {
  protected final File compileData(final String testCaseName, final String fileToCompile) {
    final File compilerOutput = getCompilerOutputPath(testCaseName);
    assertEquals(Main.compile(
      new String[]{"-g:vars", "-d", compilerOutput.getAbsolutePath(), String.format("%s/%s/%s", getTestDataPath(), testCaseName, fileToCompile)}), 0);
    return compilerOutput;
  }

  private static File getCompilerOutputPath(final String testCaseName) {
    try {
      return FileUtil.createTempDirectory(testCaseName, "_compiled");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
