// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.index;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.images.util.ImageInfo;

import java.io.IOException;

import static org.junit.Assert.assertNotEquals;

public class ImageInfoIndexTest extends BasePlatformTestCase {
  public void testIndexModification() throws IOException {
    VirtualFile file = myFixture.addFileToProject("image.svg", "<svg width='300' height='300' xmlns='http://www.w3.org/2000/svg'></svg>").getVirtualFile();
    long stamp = getIndexStamp();
    ImageInfo value = getIndexValue(file);

    VfsUtil.saveText(file, "<svg width='500' height='300' xmlns='http://www.w3.org/2000/svg'></svg>");
    assertNotEquals(stamp, getIndexStamp());
    assertNotEquals(value, getIndexValue(file));
    stamp = getIndexStamp();
    value = getIndexValue(file);

    VfsUtil.saveText(file, "<svg width='500' height='300' xmlns='http://www.w3.org/2000/svg'><path d=\"M10 10\"/></svg>");
    assertEquals(stamp, getIndexStamp());
    assertEquals(value, getIndexValue(file));
  }

  private long getIndexStamp() {
    return FileBasedIndex.getInstance().getIndexModificationStamp(ImageInfoIndex.INDEX_ID, myFixture.getProject());
  }

  private ImageInfo getIndexValue(VirtualFile file) {
    return FileBasedIndex.getInstance().getFileData(ImageInfoIndex.INDEX_ID, file, myFixture.getProject()).values().iterator().next();
  }

  @Override
  protected boolean isWriteActionRequired() {
    return true;
  }
}