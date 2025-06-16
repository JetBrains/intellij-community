// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

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
  
  public void testCreateDirectories() {
    configureFromFileText("dummy.txt", "");
    ModCommand command = ModCommand.psiUpdate(getFile(), (f, u) -> {
      PsiDirectory d = u.getWritable(getFile().getContainingDirectory());
      PsiDirectory dir1 = d.createSubdirectory("a");
      PsiDirectory dir2 = dir1.createSubdirectory("b");
      dir1.createSubdirectory("c");
      PsiFile file = dir2.createFile("x.txt");
      file.getFileDocument().insertString(0, "hello");
    });
    ModCompositeCommand compositeCommand = assertInstanceOf(command, ModCompositeCommand.class);
    String actual = compositeCommand.commands().stream().map(cmd -> {
      ModCreateFile createFile = assertInstanceOf(cmd, ModCreateFile.class);
      return createFile.file().getPath() + " | " + createFile.content() + "\n";
    }).collect(Collectors.joining());
    assertEquals("""
                   /src/a | Directory[]
                   /src/a/b | Directory[]
                   /src/a/b/x.txt | Text[text=hello]
                   /src/a/c | Directory[]
                   """, actual);
    ModCommandExecutor.executeInteractively(ActionContext.from(null, getFile()), "", null, () -> command);
    VirtualFile target = getVFile().findFileByRelativePath("../a/b/x.txt");
    assertNotNull(target);
    PsiFile targetFile = PsiManager.getInstance(getProject()).findFile(target);
    assertEquals("hello", targetFile.getFileDocument().getCharsSequence().toString());
  }
  
  public void testCreateDirectoriesPreview() {
    configureFromFileText("dummy.txt", "");
    ModCommand command = ModCommand.psiUpdate(getFile(), (f, u) -> {
      PsiDirectory d = u.getWritable(getFile().getContainingDirectory());
      PsiDirectory dir1 = d.createSubdirectory("a");
      dir1.createSubdirectory("b");
      dir1.createSubdirectory("c");
    });
    IntentionPreviewInfo preview = ModCommandExecutor.getInstance().getPreview(command, ActionContext.from(null, getFile()));
    IntentionPreviewInfo.Html html = assertInstanceOf(preview, IntentionPreviewInfo.Html.class);
    String actual = html.content().toString();
    assertEquals("<p>Create directories:<br/>&bull; a<br/>&bull; a/b<br/>&bull; a/c</p>", actual);
  }

  public void testCreateDirectoriesAndMovePreview() {
    configureFromFileText("dummy.txt", "");
    VirtualFile vFile = getFile().getVirtualFile();
    FutureVirtualFile target = new FutureVirtualFile(vFile.getParent(), "a", null);
    ModCommand command = new ModCreateFile(target, new ModCreateFile.Directory())
      .andThen(new ModMoveFile(vFile, new FutureVirtualFile(target, vFile.getName(), vFile.getFileType())));
    IntentionPreviewInfo preview = ModCommandExecutor.getInstance().getPreview(command, ActionContext.from(null, getFile()));
    IntentionPreviewInfo.Html html = assertInstanceOf(preview, IntentionPreviewInfo.Html.class);
    String actual = html.content().toString();
    assertEquals("""
                   Create directory &#39;a&#39;<br/><br/><p><icon src="file"/>&nbsp;dummy.txt &rarr; \
                   <icon src="dir"/>&nbsp;$src$a</p>""".replace("$", File.separator), actual);
  }
}
