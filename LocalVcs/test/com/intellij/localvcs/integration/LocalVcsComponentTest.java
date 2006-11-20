package com.intellij.localvcs.integration;

import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.mock.MockFileSystem;
import org.junit.Test;

public class LocalVcsComponentTest /*extends LightIdeaTestCase*/ {
  @Test
  public void testComponentInitialitation() {
    //assertNotNull(getProject().getComponent(LocalVcsComponent.class));
  }

  @Test
  public void testUpdatingVcsOnProjectOpen() {
    //ModuleRootManager m = ModuleRootManager.getInstance(getModule());
    //
    //VirtualFileSystem fs = new MockFileSystem();
    //VirtualFile file = fs.findFileByPath("dir");
    //m.getModifiableModel().addContentEntry(file);
    //m.getModifiableModel().commit();
    //VirtualFile[] roots = m.getContentRoots();
    //
  }
}
