package com.intellij.localvcs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;

public abstract class TempDirTestCase {
  protected static final String TEMP_DIR_NAME = "temp";

  @Before
  public void createTempDir() {
    deleteTempDir();
    getTempDir().mkdirs();
  }

  @After
  public void deleteTempDir() {
    FileUtil.delete(getTempDir());
    if (getTempDir().exists())
      throw new RuntimeException("can't delete temp directorty");
  }

  public File createFile(String name) {
    return createFile(name, "");
  }

  public File createFile(String name, String content) {
    try {
      File file = makeTempFile(name);
      file.createNewFile();

      FileOutputStream s = new FileOutputStream(file);
      s.write(content.getBytes());
      s.close();

      return file;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected File createDir(String name) {
    File file = makeTempFile(name);
    file.mkdirs();
    return file;
  }

  protected void deleteFile(String name) {
    makeTempFile(name).delete();
  }

  private File makeTempFile(String name) {
    return new File(getTempDir(), name);
  }

  protected File getTempDir() {
    return new File(getClass().getResource(".").getPath(), TEMP_DIR_NAME);
  }
}
