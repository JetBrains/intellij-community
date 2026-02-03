// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class ImportHelperMultiFileTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/importHelper/";

  public void testReimportConflictingClasses() throws Exception {
    String path = BASE_PATH + getTestName(true);
    configureByFile(path + "/x/Usage.java", path);
    assertEmpty(highlightErrors());

    JavaCodeStyleSettings.getInstance(getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @NonNls String fullPath = getTestDataPath() + path + "/x/Usage_afterOptimize.txt";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    String text = LoadTextUtil.loadText(vFile).toString();
    assertEquals(text, getFile().getText());
  }

  public void testConflictBetweenRegularAndStaticClassesInImportList() throws Exception {
    String path = BASE_PATH + getTestName(true);
    configureByFile(path + "/foo/A.java", path);
    assertEmpty(highlightErrors());

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    assertEmpty(highlightErrors());
  }
}
