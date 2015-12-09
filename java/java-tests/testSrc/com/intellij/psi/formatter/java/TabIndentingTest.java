/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class TabIndentingTest extends LightIdeaTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/formatter/tabIndenting";

  public void testSpace() throws Exception {
    doTest("Test.java", "Space_after.java");
  }

  public void testSmartTab4() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;

    try{
      doTest("Test.java", "SmartTab4_after.java");
    }
    finally{
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = false;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = false;
    }
  }

  public void testSmartTab2() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
    settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 2;

    try{
      doTest("Test.java", "SmartTab2_after.java");
    }
    finally{
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = false;
      settings.getIndentOptions(StdFileTypes.JAVA).SMART_TABS = false;
      settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 4;
    }
  }

  //does not work correctly at the moment
  /*
  public void testSmartTab8() throws Exception {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    editorSettings.setUseTabCharacter(true);
    //editorSettings.setTabSize(8);

    try{
      doTest("Test.java", "SmartTab8_after.java");
    }
    finally{
      editorSettings.setUseTabCharacter(false);
      //editorSettings.setTabSize(8);
    }
  }
  */

  public void testTab2() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 2;

    try{
      doTest("Test.java", "Tab2_after.java");
    }
    finally{
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = false;
      settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 4;
    }
  }

  public void testTab4() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;

    try{
      doTest("Test.java", "Tab4_after.java");
    }
    finally{
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = false;
    }
  }

  public void testTab8() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 8;

    try{
      doTest("Test.java", "Tab8_after.java");
    }
    finally{
      settings.getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = false;
      settings.getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 4;
    }
  }

  public void testSCR6197() throws Exception {
    doTest("SCR6197.java", "SCR6197_after.java");
  }

  private void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    String text = loadFile(fileNameBefore);
    final PsiFile file = createFile(fileNameBefore, text);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              CodeStyleManager.getInstance(getProject()).reformat(file);
            }
            catch (IncorrectOperationException e) {
              assertTrue(false);
            }
          }
        });
      }
    }, null, null);


    String textAfter = loadFile(fileNameAfter);
    String fileText = file.getText();

    textAfter = StringUtil.trimStart(textAfter, "\n");
    fileText = StringUtil.trimStart(fileText, "\n");

    if (!textAfter.equals(fileText)) {
      System.err.println("Expected:");
      System.err.println(textAfter);
      System.err.println();
      System.err.println("Was:");
      System.err.println(fileText);

      assertEquals(textAfter, fileText);
    }
  }

  private String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
