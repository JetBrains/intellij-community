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

package com.intellij.historyIntegrTests;


import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Revision;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

public class ExternalChangesAndRefreshingTest extends IntegrationTestCase {
  public void testRefreshingSynchronously() throws Exception {
    doTestRefreshing(false);
  }

  public void testRefreshingAsynchronously() throws Exception {
    doTestRefreshing(true);
  }

  @Override
  protected void runBareRunnable(Runnable r) throws Throwable {
    if (getName().equals("testRefreshingAsynchronously")) {
      // this methods waits for another thread to finish, that leds
      // to deadlock in swing-thread. Therefore we have to run this test
      // outside of swing-thread
      r.run();
    }
    else {
      super.runBareRunnable(r);
    }
  }

  private void doTestRefreshing(boolean async) throws Exception {
    int before = getRevisionsFor(myRoot).size();

    createFileExternally("f1.txt");
    createFileExternally("f2.txt");

    assertEquals(before, getRevisionsFor(myRoot).size());

    refreshVFS(async);

    assertEquals(before + 1, getRevisionsFor(myRoot).size());
  }

  public void testChangeSetName() throws Exception {
    createFileExternally("f.txt");
    refreshVFS();
    Revision r = getRevisionsFor(myRoot).get(1);
    assertEquals("External change", r.getChangeSetName());
  }

  public void testRefreshDuringCommand() {
    // shouldn't throw
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        refreshVFS();
      }
    }, "", null);
  }

  public void testCommandDuringRefresh() throws Exception {
    createFileExternally("f.txt");

    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        executeSomeCommand();
      }
    };

    // shouldn't throw
    addFileListenerDuring(l, new Runnable() {
      public void run() {
        refreshVFS();
      }
    });
  }

  private void executeSomeCommand() {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
      }
    }, "", null);
  }

  public void testFileCreationDuringRefresh() throws Exception {
    final String path = createFileExternally("f.txt");
    setContentExternally(path, "content");

    final String[] content = new String[1];
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        try {
          if (!e.getFile().getPath().equals(path)) return;
          content[0] = new String(e.getFile().contentsToByteArray());
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    };

    addFileListenerDuring(l, new Runnable() {
      public void run() {
        refreshVFS();
      }
    });
    assertEquals("content", content[0]);
  }

  public void testDeletionOfFilteredDirectoryExternallyDoesNotThrowExceptionDuringRefresh() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    myRoot.createChildDirectory(null, FILTERED_DIR_NAME);
    String path = Paths.appended(myRoot.getPath(), FILTERED_DIR_NAME);

    new File(path).delete();
    assertEquals(before, getRevisionsFor(myRoot).size());

    refreshVFS();

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testCreationOfExcludedDirWithFilesDuringRefreshShouldNotThrowException() throws Exception {
    // there was a problem with the DirectoryIndex - the files that were created during the refresh
    // were not correctly excluded, thereby causing the LocalHistory to fail during addition of 
    // files under the excluded dir.

    File targetDir = createTargetDir();

    FileUtil.copyDir(targetDir, new File(myRoot.getPath(), "target"));
    VirtualFileManager.getInstance().refresh(false);

    String classesPath = myRoot.getPath() + "/target/classes";
    addExcludedDir(classesPath);
    LocalFileSystem.getInstance().findFileByPath(classesPath).getParent().delete(null);

    FileUtil.copyDir(targetDir, new File(myRoot.getPath(), "target"));
    VirtualFileManager.getInstance().refresh(false); // shouldn't throw
  }

  private File createTargetDir() throws IOException {
    File result = createTempDirectory();
    File classes = new File(result, "classes");
    classes.mkdir();
    new File(classes, "bak.txt").createNewFile();
    return result;
  }

  private void refreshVFS() {
    refreshVFS(false);
  }

  private void refreshVFS(boolean async) {
    try {
      final Semaphore s = new Semaphore(1);
      s.acquire();
      VirtualFileManager.getInstance().refresh(async, new Runnable() {
        public void run() {
          s.release();
        }
      });
      s.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}