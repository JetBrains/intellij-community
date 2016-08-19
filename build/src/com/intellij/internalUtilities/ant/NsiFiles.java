/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.internalUtilities.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.FileSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Ant task to facilitate building NSIS installer.
 *
 * @author  max
 * @since Jan 11, 2005
 */
@SuppressWarnings("UnusedDeclaration")
public class NsiFiles extends MatchingTask {
  private File myInstFile;
  private File myUnInstFile;
  private File myBaseDir;
  private final List<FileSet> myFileSets = new ArrayList<>();
  private final Map<String, List<String>> myDirToFiles = new LinkedHashMap<>();
  private final Map<String, String> myAbsoluteToRelative = new HashMap<>();

  /**
   * The file to create; required
   * @param nsiFile new file to generate file install list to.
   */
  public void setInstFile(File nsiFile) {
    myInstFile = nsiFile;
  }

  /**
   * The file to create; required
   * @param nsiFile new file to generate file uninstall list to.
   */
  public void setUninstFile(File nsiFile) {
    myUnInstFile = nsiFile;
  }

  /**
   * ;optional
   * @param fileSet to be included in processing
   */
  public void addFileSet(FileSet fileSet) {
    myFileSets.add(fileSet);
  }

  /**
   * ;optional
   * @param baseDir of the files to be processed.
   */
  public void setBaseDir(File baseDir) {
    myBaseDir = baseDir;
  }

  public void execute() throws BuildException {
    if (myInstFile == null) throw new BuildException("Specify required isntFile attribute.");
    if (myUnInstFile == null) throw new BuildException("Specify required unisntFile attribute.");

    if (myBaseDir == null && myFileSets.size() == 0) {
        throw new BuildException("basedir attribute must be set, "
                                 + "or at least "
                                 + "one fileset must be given!");
    }

    try {
      if (myBaseDir != null) {
        FileSet fs = (FileSet) getImplicitFileSet().clone();
        fs.setDir(myBaseDir);
        processFileSet(fs);
      }

      for (FileSet fileSet : myFileSets) {
        processFileSet(fileSet);
      }

      generateInstFile();
      generateUninstFile();
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
    finally {
      cleanup();
    }
  }

  private void generateUninstFile() throws IOException {
    BufferedWriter uninstWriter = new BufferedWriter(new FileWriter(myUnInstFile));
    try {
      List<String> allFiles = new ArrayList<>();
      final Collection<List<String>> lists = myDirToFiles.values();
      for (final List<String> list : lists) {
        allFiles.addAll(list);
      }

      Collections.sort(allFiles);
      for (String file : allFiles) {
        uninstWriter.newLine();
        final String relPath = myAbsoluteToRelative.get(file);
        uninstWriter.write("Delete \"$INSTDIR\\" + toWinPath(relPath) + "\"");
        if (relPath.endsWith(".py")) {
          uninstWriter.newLine();
          uninstWriter.write("Delete \"$INSTDIR\\" + toWinPath(relPath) + "c\"");  // .pyc
        }
      }

      uninstWriter.newLine();
      List<String> dirs = new ArrayList<>(myDirToFiles.keySet());
      Collections.sort(dirs);
      for (int i = dirs.size() - 1; i >= 0; i--) {
        final String dir = dirs.get(i);
        if (dir.length() == 0) continue;
        uninstWriter.newLine();
        uninstWriter.write("RmDir /r \"$INSTDIR\\" + toWinPath(dir) + "\\__pycache__\"");
        uninstWriter.newLine();
        uninstWriter.write("RmDir \"$INSTDIR\\" + toWinPath(dir) + "\"");
      }
      uninstWriter.newLine();
      uninstWriter.write("RmDir \"$INSTDIR\"");
    }
    finally{
      uninstWriter.close();
    }
  }

  private void generateInstFile() throws IOException {
    BufferedWriter instWriter = new BufferedWriter(new FileWriter(myInstFile));
    try {
      Collection<String> dirs = myDirToFiles.keySet();
      for (String dir : dirs) {
        final List<String> files = myDirToFiles.get(dir);
        if (files.size() == 0) continue;
        instWriter.newLine();
        instWriter.newLine();
        if (dir.length() > 0) {
          instWriter.write("SetOutPath \"$INSTDIR" + "\\" + toWinPath(dir) + "\"");
        }
        else {
          instWriter.write("SetOutPath \"$INSTDIR\"");
        }

        for (String file : files) {
          instWriter.newLine();
          instWriter.write("File \"" + file + "\"");
        }
      }
    }
    finally {
      instWriter.close();
    }
  }

  private static String toWinPath(String dir) {
    return File.separatorChar == '\\' ? dir : dir.replaceAll(File.separator, Matcher.quoteReplacement("\\"));
  }

  private void processFileSet(final FileSet fileSet) throws IOException {
    final DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
    final String[] files = scanner.getIncludedFiles();
    String base = fileSet.getDir(getProject()).getCanonicalPath() + File.separator;
    for (String file : files) {
      String lastDir = "";
      getDirFileList(lastDir);
      int idx = -1;
      do {
        idx = file.indexOf(File.separator, idx + 1);
        if (idx == -1) break;
        lastDir = file.substring(0, idx);
        getDirFileList(lastDir);
      }
      while (true);

      List<String> fileList = getDirFileList(lastDir);
      final String absolute = base + file;
      fileList.add(absolute);
      myAbsoluteToRelative.put(absolute, file);
    }
  }

  private List<String> getDirFileList(final String dir) {
    List<String> fileList = myDirToFiles.get(dir);
    if (fileList == null) {
      fileList = new ArrayList<>();
      myDirToFiles.put(dir, fileList);
    }
    return fileList;
  }

  private void cleanup() {
    myBaseDir = null;
    myInstFile = null;
    myFileSets.clear();
    myDirToFiles.clear();
    myAbsoluteToRelative.clear();
  }
}
