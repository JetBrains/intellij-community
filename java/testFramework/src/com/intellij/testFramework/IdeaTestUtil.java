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
package com.intellij.testFramework;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.ZipUtil;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;

public class IdeaTestUtil extends PlatformTestUtil {

  /**
   * Measured on dual core p4 3HZ 1gig ram
   */
  private static final long ETALON_TIMING = 438;


  public static final Comparator<AbstractTreeNode> DEFAULT_COMPARATOR = new Comparator<AbstractTreeNode>() {
    public int compare(AbstractTreeNode o1, AbstractTreeNode o2) {
      String displayText1 = o1.getTestPresentation();
      String displayText2 = o2.getTestPresentation();
      return displayText1.compareTo(displayText2);
    }
  };

  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));
  public static final CvsVirtualFileFilter CVS_FILE_FILTER = new CvsVirtualFileFilter();

  private static HashMap<String, VirtualFile> buildNameToFileMap(VirtualFile[] files, VirtualFileFilter filter) {
    HashMap<String, VirtualFile> map = new HashMap<String, VirtualFile>();
    for (VirtualFile file : files) {
      if (filter != null && !filter.accept(file)) continue;
      map.put(file.getName(), file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(VirtualFile dirAfter, VirtualFile dirBefore, VirtualFileFilter fileFilter) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();
    VirtualFile[] childrenAfter = dirAfter.getChildren();
    File[] ioAfter = new File(dirAfter.getPath()).listFiles();
    shallowCompare(childrenAfter, ioAfter);
    VirtualFile[] childrenBefore = dirBefore.getChildren();
    File[] ioBefore = new File(dirBefore.getPath()).listFiles();
    shallowCompare(childrenBefore, ioBefore);

    HashMap<String, VirtualFile> mapAfter = buildNameToFileMap(childrenAfter, fileFilter);
    HashMap<String, VirtualFile> mapBefore = buildNameToFileMap(childrenBefore, fileFilter);

    Set<String> keySetAfter = mapAfter.keySet();
    Set<String> keySetBefore = mapBefore.keySet();
    Assert.assertEquals(keySetAfter, keySetBefore);

    for (String name : keySetAfter) {
      VirtualFile fileAfter = mapAfter.get(name);
      VirtualFile fileBefore = mapBefore.get(name);
      if (fileAfter.isDirectory()) {
        assertDirectoriesEqual(fileAfter, fileBefore, fileFilter);
      }
      else {
        assertFilesEqual(fileAfter, fileBefore);
      }
    }
  }

  private static void shallowCompare(final VirtualFile[] vfs, final File[] io) {
    List<String> vfsPaths = new ArrayList<String>();
    for (VirtualFile file : vfs) {
      vfsPaths.add(file.getPath());
    }

    List<String> ioPaths = new ArrayList<String>();
    for (File file : io) {
      ioPaths.add(file.getPath().replace(File.separatorChar, '/'));
    }

    Assert.assertEquals(sortAndJoin(vfsPaths), sortAndJoin(ioPaths));
  }

  private static String sortAndJoin(List<String> strings) {
    Collections.sort(strings);
    StringBuilder buf = new StringBuilder();
    for (String string : strings) {
      buf.append(string);
      buf.append('\n');
    }
    return buf.toString();
  }

  public static void assertFilesEqual(VirtualFile fileAfter, VirtualFile fileBefore) throws IOException {
    assertJarFilesEqual(VfsUtil.virtualToIoFile(fileAfter), VfsUtil.virtualToIoFile(fileBefore));
  }

  public static void assertJarFilesEqual(File file1, File file2) throws IOException {
    final JarFile jarFile1;
    final JarFile jarFile2;
    try {
      jarFile1 = new JarFile(file1);
      jarFile2 = new JarFile(file2);
    }
    catch (IOException e) {
      String textAfter = String.valueOf(FileUtil.loadFileText(file1));
      String textBefore = String.valueOf(FileUtil.loadFileText(file2));
      textAfter = StringUtil.convertLineSeparators(textAfter);
      textBefore = StringUtil.convertLineSeparators(textBefore);
      Assert.assertEquals(file1.getPath(), textAfter, textBefore);
      return;
    }

    final File tempDirectory1 = IdeaTestCase.createTempDir("tmp1");
    final File tempDirectory2 = IdeaTestCase.createTempDir("tmp2");
    ZipUtil.extract(jarFile1, tempDirectory1, CVS_FILE_FILTER);
    ZipUtil.extract(jarFile2, tempDirectory2, CVS_FILE_FILTER);
    jarFile1.close();
    jarFile2.close();
    final VirtualFile dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory1);
    final VirtualFile dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory2);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        dirAfter.refresh(false, true);
        dirBefore.refresh(false, true);
      }
    });
    assertDirectoriesEqual(dirAfter, dirBefore, CVS_FILE_FILTER);
  }

  public static void assertTiming(String message, long expected, long actual) {
    if (COVERAGE_ENABLED_BUILD) return;
    long expectedOnMyMachine = expected * Timings.MACHINE_TIMING / ETALON_TIMING;
    final double acceptableChangeFactor = 1.1;

    // Allow 10% more in case of test machine is busy.
    // For faster machines (expectedOnMyMachine < expected) allow nonlinear performance rating:
    // just perform better than acceptable expected
    if (actual > expectedOnMyMachine * acceptableChangeFactor &&
        (expectedOnMyMachine > expected || actual > expected * acceptableChangeFactor)) {
      int percentage = (int)(((float)100 * (actual - expectedOnMyMachine)) / expectedOnMyMachine);
      Assert.fail(message + ". Operation took " + percentage + "% longer than expected. Expected on my machine: " + expectedOnMyMachine +
                  ". Actual: " + actual + ". Expected on Etalon machine: " + expected + "; Actual on Etalon: " +
                  (actual * ETALON_TIMING / Timings.MACHINE_TIMING));
    }
    else {
      int percentage = (int)(((float)100 * (actual - expectedOnMyMachine)) / expectedOnMyMachine);
      System.out.println(message + ". Operation took " + percentage + "% longer than expected. Expected on my machine: " +
                         expectedOnMyMachine + ". Actual: " + actual + ". Expected on Etalon machine: " + expected +
                         "; Actual on Etalon: " + (actual * ETALON_TIMING / Timings.MACHINE_TIMING));
    }
  }

  public static void assertTiming(String message, long expected, @NotNull Runnable actionToMeasure) {
    assertTiming(message, expected, 4, actionToMeasure);
  }

  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      long start = System.currentTimeMillis();
      actionToMeasure.run();
      long finish = System.currentTimeMillis();
      try {
        assertTiming(message, expected, finish - start);
        break;
      }
      catch (AssertionFailedError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
      }
    }
  }

  public static void main(String[] args) {
    printDetectedPerformanceTimings();
  }

  public static void printDetectedPerformanceTimings() {
    System.out.println("Etalon timing: " + ETALON_TIMING);
    System.out.println("This machine timing: " + Timings.MACHINE_TIMING);
  }

  public static class CvsVirtualFileFilter implements VirtualFileFilter, FilenameFilter {
    public boolean accept(VirtualFile file) {
      return !file.isDirectory() || !"CVS".equals(file.getName());
    }

    public boolean accept(File dir, String name) {
      return name.indexOf("CVS") == -1;
    }
  }
}