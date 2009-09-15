package com.intellij.historyIntegrTests;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class IdeaGatewayTest extends IntegrationTestCase {
  public void testFindingFile() throws Exception {
    assertSame(root, gateway.findVirtualFile(root.getPath()));
    assertNull(gateway.findVirtualFile(root.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertSame(root, gateway.findOrCreateFileSafely(root.getPath(), true));
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = gateway.findOrCreateFileSafely(subSubDirPath, true);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testCreatingDirectoryWhenSuchFileExists() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    root.createChildData(this, "subDir");

    VirtualFile subDir = gateway.findOrCreateFileSafely(subSubDirPath, true);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }
}
