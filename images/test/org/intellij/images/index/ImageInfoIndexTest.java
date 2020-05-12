// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.index;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.images.util.ImageInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static org.junit.Assert.assertNotEquals;

public class ImageInfoIndexTest extends BasePlatformTestCase {
  public void testIndexModification() throws IOException {
    VirtualFile file = myFixture.addFileToProject("image.svg", "<svg width='300' height='300' xmlns='http://www.w3.org/2000/svg'></svg>").getVirtualFile();
    ImageInfo value = getIndexValue(file);

    VfsUtil.saveText(file, "<svg width='500' height='300' xmlns='http://www.w3.org/2000/svg'></svg>");
    assertNotEquals(value, getIndexValue(file));
    value = getIndexValue(file);

    VfsUtil.saveText(file, "<svg width='500' height='300' xmlns='http://www.w3.org/2000/svg'><path d=\"M10 10\"/></svg>");
    assertEquals(value, getIndexValue(file));
  }

  public void testIndexingSameImages() throws IOException {
    String text = "<svg width='300' height='300' xmlns='http://www.w3.org/2000/svg'></svg>";
    VirtualFile file1 = myFixture.addFileToProject("image1.svg", text).getVirtualFile();
    VirtualFile file2 = myFixture.addFileToProject("image2.svg", text).getVirtualFile();

    assertEquals(getIndexValue(file1), getIndexValue(file2));
    assertEquals(300, getIndexValue(file1).width);
    assertEquals(300, getIndexValue(file2).width);

    VfsUtil.saveText(file1, "<svg width='500' height='300' xmlns='http://www.w3.org/2000/svg'></svg>");
    assertEquals(500, getIndexValue(file1).width);
    assertEquals(300, getIndexValue(file2).width);
  }

  private ImageInfo getIndexValue(@NotNull VirtualFile file) {
    return ImageInfoIndex.getInfo(file, myFixture.getProject());
  }

  @Override
  protected boolean isWriteActionRequired() {
    return true;
  }
}