package com.intellij.history.core;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.net.URISyntaxException;

public abstract class TempDirTestCase extends LocalVcsTestCase {
  protected static File classTempDir;
  protected File tempDir;

  @BeforeClass
  public static void createClassTempDir() {
    classTempDir = createDir("classTempDir");
  }

  @Before
  public void createTempDir() {
    tempDir = createDir("tempDir");
  }

  @AfterClass
  public static void deleteClassTempDir() {
    deleteDir(classTempDir);
  }

  @After
  public void deleteTempDir() {
    deleteDir(tempDir);
  }

  private static File createDir(String name) {
    try {
      File root = new File(TempDirTestCase.class.getResource(".").toURI());
      File result = new File(root, name);

      deleteDir(result);
      result.mkdirs();

      return result;
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static void deleteDir(File dir) {
    if (!FileUtil.delete(dir)) throw new RuntimeException("can't delete dir <" + dir.getName() + ">");
  }
}
