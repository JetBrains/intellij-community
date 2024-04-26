/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.core;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.net.URISyntaxException;

public abstract class TempDirTestCase extends LocalHistoryTestCase {
  protected static File classTempDir;
  protected File myTempDir;

  @BeforeClass
  public static void createClassTempDir() {
    classTempDir = createDir("classTempDir");
  }

  @Before
  public void createTempDir() {
    myTempDir = createDir("tempDir");
  }

  @AfterClass
  public static void deleteClassTempDir() {
    deleteDir(classTempDir);
  }

  @After
  public void deleteTempDir() {
    deleteDir(myTempDir);
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
    if (!dir.exists()) return;
    if (!FileUtil.delete(dir)) throw new RuntimeException("can't delete dir <" + dir.getName() + ">");
  }
}
