// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.IOException;

public final class ModCommandTest extends LightPlatformCodeInsightTestCase {
  public void testBinaryFile() throws IOException {
    configureFromFileText("dummy.txt", "");
    VirtualFile root = getSourceRoot();
    FutureVirtualFile futureFile = new FutureVirtualFile(root, "test.dat", UserBinaryFileType.INSTANCE);
    byte[] content = {'h', 'e', 'l', 'l', 'o'};
    ModCreateFile command = new ModCreateFile(futureFile, new ModCreateFile.Binary(content));
    ModCommandExecutor executor = ModCommandExecutor.getInstance();
    ActionContext context = ActionContext.from(null, getFile());
    IntentionPreviewInfo preview = executor.getPreview(command, context);
    assertEquals(new IntentionPreviewInfo.CustomDiff(UserBinaryFileType.INSTANCE, "test.dat", "", "(binary content)", true), preview);
    ModCommandExecutor.BatchExecutionResult result = executor.executeInBatch(context, command);
    assertEquals(ModCommandExecutor.Result.SUCCESS, result);
    VirtualFile child = root.findChild("test.dat");
    assertNotNull(child);
    assertOrderedEquals(content, child.contentsToByteArray());
  }
  
  public void testBrowse() {
    configureFromFileText("dummy.txt", "");
    ActionContext context = ActionContext.from(null, getFile());
    ModCommand command = ModCommand.openUrl("https://example.com");
    ModCommandExecutor executor = ModCommandExecutor.getInstance();
    IntentionPreviewInfo preview = executor.getPreview(command, context);
    assertEquals(new IntentionPreviewInfo.Html(HtmlChunk.text("Browse \"https://example.com\"")), preview);
    ModCommandExecutor.BatchExecutionResult result = executor.executeInBatch(context, command);
    assertEquals(ModCommandExecutor.Result.INTERACTIVE, result);
  }
}
