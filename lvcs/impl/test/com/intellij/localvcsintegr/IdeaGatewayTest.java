package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class IdeaGatewayTest extends IntegrationTestCase {
  IdeaGateway gw;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    gw = new IdeaGateway(myProject);
  }

  public void testFindingFile() throws Exception {
    assertSame(root, gw.findVirtualFile(root.getPath()));
    assertNull(gw.findVirtualFile(root.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertSame(root, gw.findOrCreateDirectory(root.getPath()));
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = gw.findOrCreateDirectory(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }
}
