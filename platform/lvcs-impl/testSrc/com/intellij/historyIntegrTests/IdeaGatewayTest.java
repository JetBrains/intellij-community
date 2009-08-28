package com.intellij.historyIntegrTests;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class IdeaGatewayTest extends IntegrationTestCase {
  public void testFindingFile() throws Exception {
    assertSame(root, gateway.findVirtualFile(root.getPath()));
    assertNull(gateway.findVirtualFile(root.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertSame(root, gateway.findOrCreateDirectory(root.getPath()));
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = gateway.findOrCreateDirectory(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }
}
