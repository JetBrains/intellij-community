// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.revertion;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class RevertToLabelTest extends IntegrationTestCase {

  public void testFileCreation() throws Exception {
    String fileBeforeLabel = "first.txt";
    String fileAfterLabel = "second.txt";

    createChildData(myRoot, fileBeforeLabel);
    Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    createChildData(myRoot, fileAfterLabel);

    testLabel.revert(myProject, myRoot);

    assertNull(myRoot.findChild(fileAfterLabel));
    assertNotNull(myRoot.findChild(fileBeforeLabel));
  }

  public void testFileCreationAsFirstAction() throws Exception {
    Label beforeFileCreated = LocalHistory.getInstance().putSystemLabel(myProject, "beforeFileCreated");

    String fileName = "foo.txt";
    createChildData(myRoot, fileName);

    beforeFileCreated.revert(myProject, myRoot);

    assertNull(myRoot.findChild(fileName));
  }

  public void testPutLabelAndRevertInstantly() throws Exception {
    String fileName = "foo.txt";
    byte content = 123;
    createFile(fileName, content);

    Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    testLabel.revert(myProject, myRoot);

    VirtualFile file = myRoot.findChild(fileName);
    assertNotNull(file);
    assertEquals(content, file.contentsToByteArray()[0]);
  }

  public void testFileDeletion() throws Exception {
    String fileName = "foo.txt";
    byte content = 123;
    VirtualFile file = createFile(fileName, content);

    Label beforeDeletion = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    delete(file);
    beforeDeletion.revert(myProject, myRoot);

    file = myRoot.findChild(fileName);
    assertNotNull(file);
    assertEquals(123, file.contentsToByteArray()[0]);
  }

  public void testFileDeletionWithContent() throws Exception {
    String fileName = "foo.txt";
    VirtualFile file = createChildData(myRoot, fileName);

    Label beforeContentChange = LocalHistory.getInstance().putSystemLabel(myProject, "beforeContentChange");
    setContent(file, 123);
    delete(file);
    beforeContentChange.revert(myProject, myRoot);

    file = myRoot.findChild(fileName);
    assertNotNull(file);
    assertEquals(0, file.contentsToByteArray().length);
  }

  public void testParentAndChildRename() throws Exception {
    String oldDirName = "dir";
    String oldFileName = "foo.txt";
    byte content = 123;
    VirtualFile dir = createChildDirectory(myRoot, oldDirName);
    VirtualFile file = createChildData(dir, oldFileName);
    setContent(file, content);

    String newDirName = "dir2";
    String newFileName = "bar.txt";

    LocalHistory localHistory = LocalHistory.getInstance();
    Label beforeDirRename = localHistory.putSystemLabel(myProject, "beforeDirRename");
    rename(dir, newDirName);
    Label beforeFileRename = localHistory.putSystemLabel(myProject, "beforeFileRename");
    rename(file, newFileName);

    beforeFileRename.revert(myProject, file);

    dir = myRoot.findChild(newDirName);
    assertNotNull(dir);

    assertNull(dir.findChild(newFileName));
    file = dir.findChild(oldFileName);
    assertNotNull(file);
    assertEquals(content, file.contentsToByteArray()[0]);

    beforeDirRename.revert(myProject, myRoot);

    assertNull(myRoot.findChild(newDirName));
    dir = myRoot.findChild(oldDirName);
    assertNotNull(dir);
    assertNull(dir.findChild(newFileName));

    file = dir.findChild(oldFileName);
    assertNotNull(file);
    assertEquals(content, file.contentsToByteArray()[0]);
  }

  public void testRevertContentChange() throws Exception {
    String fileName = "foo.txt";
    byte initialContent = 1;
    VirtualFile file = createFile(fileName, initialContent);

    Label beforeFileModified = LocalHistory.getInstance().putSystemLabel(myProject, "initialFileContent");
    for (byte content : new byte[]{2, 3, 4}) {
      setContent(file, content);
    }
    beforeFileModified.revert(myProject, myRoot);

    file = myRoot.findChild(fileName);
    assertNotNull(file);
    assertEquals(initialContent, file.contentsToByteArray()[0]);
  }

  public void testRevertContentChangeOnlyForFile() throws Exception {
    String fileName1 = "foo.txt";
    String fileName2 = "foo2.txt";
    byte initialContent = 1;
    VirtualFile file1 = createFile(fileName1, initialContent);
    VirtualFile file2 = createFile(fileName2, initialContent);

    Label beforeModifications = LocalHistory.getInstance().putSystemLabel(myProject, "beforeModifications");
    byte lastContent = 10;
    for (byte content : new byte[]{2, 3, 4, lastContent}) {
      setContent(file1, content);
      setContent(file2, content);
    }
    beforeModifications.revert(myProject, file1);

    file1 = myRoot.findChild(fileName1);
    assertNotNull(file1);
    assertEquals(initialContent, file1.contentsToByteArray()[0]);

    file2 = myRoot.findChild(fileName2);
    assertNotNull(file2);
    assertEquals(lastContent, file2.contentsToByteArray()[0]);
  }

  private @NotNull VirtualFile createFile(@NotNull String fileName, byte content) {
    VirtualFile file = createChildData(myRoot, fileName);
    setContent(file, content);
    return file;
  }
}
