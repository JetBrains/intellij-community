package com.intellij.localvcs;

import java.io.File;
import java.net.URISyntaxException;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;

public abstract class TempDirTestCase extends TestCase {
  protected File myTempDir;

  @Before
  public void createTempDir() {
    try {
      File root = new File(getClass().getResource(".").toURI());
      myTempDir = new File(root, "temp");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    deleteTempDir();
    myTempDir.mkdirs();
  }

  @After
  public void deleteTempDir() {
    if (!FileUtil.delete(myTempDir))
      throw new RuntimeException("can't delete temp dir");
  }
}
