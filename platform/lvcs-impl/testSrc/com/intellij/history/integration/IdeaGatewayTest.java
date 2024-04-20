// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class IdeaGatewayTest extends IntegrationTestCase {
  public void testFindingFile() {
    assertEquals(myRoot, myGateway.findVirtualFile(myRoot.getPath()));
    assertNull(myGateway.findVirtualFile(myRoot.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertEquals(myRoot, findOrCreateFileSafely(myRoot.getPath()));
  }

  @NotNull
  private VirtualFile findOrCreateFileSafely(String path) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(
      (ThrowableComputable<VirtualFile, IOException>)() -> myGateway.findOrCreateFileSafely(path, true))
      ;
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = myRoot.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = findOrCreateFileSafely(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testCreatingDirectoryWhenSuchFileExists() throws Exception {
    String subSubDirPath = myRoot.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    createChildData(myRoot, "subDir");

    VirtualFile subDir = findOrCreateFileSafely(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testSingleFileRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile file = createFile(lowLevelDir + "/path.txt");

    createFile(lowLevelDir + "/path2.txt");
    createFile(lowLevelDir + "/path3.txt");
    createFile(topLevelDir + "/dir3/path.txt");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(file), false);
    assertEquals(myGateway.getPathOrUrl(file), getAllPaths(rootEntry));
  }

  public void testSingleDirectoryRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile directory = createDirectory(lowLevelDir);

    createDirectory(lowLevelDir + "smth");
    createFile(lowLevelDir + "/file.txt");
    createDirectory(topLevelDir + "/dir239");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(directory), false);
    assertEquals(myGateway.getPathOrUrl(directory), getAllPaths(rootEntry));
  }

  public void testSingleDirectoryWithChildrenRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile directory = createDirectory(lowLevelDir);

    VirtualFile file1 = createFile(lowLevelDir + "/file1.txt");
    VirtualFile file2 = createFile(lowLevelDir + "/file2.txt");

    createDirectory(lowLevelDir + "smth");
    createDirectory(topLevelDir + "/dir239");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(directory), true);
    assertEquals(myGateway.getPathOrUrl(file1) + "\n" + myGateway.getPathOrUrl(file2), getAllPaths(rootEntry));
  }

  public static @NotNull String getAllPaths(@NotNull RootEntry rootEntry) {
    StringBuilder result = new StringBuilder();
    printAllPaths(rootEntry, result);
    return result.toString();
  }

  private static void printAllPaths(@NotNull Entry parentEntry, @NotNull StringBuilder result) {
    for (Entry entry : parentEntry.getChildren()) {
      if (entry.getChildren().isEmpty()) {
        if (!result.isEmpty()) result.append("\n");
        result.append(entry.getPath());
        continue;
      }
      printAllPaths(entry, result);
    }
  }
}
