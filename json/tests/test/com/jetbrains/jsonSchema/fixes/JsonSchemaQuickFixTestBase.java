// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public abstract class JsonSchemaQuickFixTestBase extends JsonSchemaHighlightingTestBase {
  protected void doTest(@Language("JSON") @NotNull String schema, @NotNull String text, String fixName, String afterFix) throws Exception {
    PsiFile file = configureInitially(schema, text);
    HashMap<VirtualFile, EditorInfo> map = new HashMap<>();
    map.put(file.getVirtualFile(), new EditorInfo(file.getText()));
    List<Editor> editors = openEditors(map);
    Collection<HighlightInfo> infos = doDoTest(true, false);
    PsiFile psiFile = getPsiFile(editors.get(0).getDocument());
    findAndInvokeIntentionAction(infos, fixName, editors.get(0), psiFile);
    assertEquals(afterFix, getFile().getText());
  }

  @NotNull
  @Override
  protected PsiFile doCreateFile(@NotNull String text) throws Exception {
    File dir = createTempDir("json_schema_test_r", true);
    File child = new File(dir, getTestFileName());
    //noinspection ResultOfMethodCallIgnored
    child.createNewFile();
    FileUtil.writeToFile(child, text);
    VirtualFile schemaFile = getVirtualFile(child);
    schemaFile.setWritable(true);
    return getPsiManager().findFile(schemaFile);
  }
}
