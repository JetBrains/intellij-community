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
package com.intellij.formatting.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class FileSetProcessor {
  private static final Logger LOG = Logger.getInstance("#" + FileSetProcessor.class.getName());

  private File myRoot;
  private String myPattern;
  private int myProcessedFiles;

  FileSetProcessor(@NotNull String fileSpec) {
    setRootAndPattern(fileSpec);
  }

  public void processFiles() throws IOException {
    processEntry(myRoot);
  }

  private void processEntry(@NotNull File entry) throws IOException {
    if (entry.exists()) {
      if (entry.isDirectory()) {
        LOG.info("Scanning directory " + entry.getPath());
        File[] subEntries = entry.listFiles();
        if (subEntries != null) {
          for (File subEntry : subEntries) {
            processEntry(subEntry);
          }
        }
      }
      else {
        if (myPattern == null || entry.getCanonicalPath().matches(myPattern)) {
          VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(entry);
          if (virtualFile == null) {
            throw new IOException("Can not find " + entry.getPath());
          }
          LOG.info("Processing " + virtualFile.getPath());
          processFile(virtualFile);
          myProcessedFiles ++;
        }
      }
    }
  }

  protected abstract void processFile(@NotNull VirtualFile virtualFile);

  private void setRootAndPattern(@NotNull String fileSpec) {
    String rootPath = fileSpec;
    int starPos = fileSpec.indexOf("*");
    int questionPos = fileSpec.indexOf("?");
    int wildcardPos = starPos >= 0 ? questionPos >= 0 ? Math.min(starPos, questionPos) : starPos : questionPos;
    if (wildcardPos >= 0) rootPath = rootPath.substring(0, wildcardPos);
    int lastSlash = Math.max(rootPath.lastIndexOf("/"), rootPath.lastIndexOf("\\"));
    rootPath = lastSlash >= 0 ? rootPath.substring(0, lastSlash) : "";
    myPattern = fileSpecToRegExp(fileSpec);
    myRoot = new File(rootPath);
  }

  private static String fileSpecToRegExp(@NotNull String fileSpec) {
    StringBuilder result = new StringBuilder();
    char[] fileSpecChars = fileSpec.toCharArray();
    for (int i = 0; i < fileSpecChars.length; i ++) {
      char c = fileSpecChars[i];
      switch (c) {
        case '.':
          result.append("\\.");
          break;
        case '*':
          char next = i + 1 < fileSpecChars.length ? fileSpecChars[i + 1] : 0;
          result.append(next == '*' ? ".*" : "[^\\\\]*");
          break;
        case '?':
          result.append(".");
          break;
        case '/':
          result.append("[\\\\/]");
          break;
        case '\\':
          result.append("[\\\\/]");
          break;
        default:
          result.append(c);
          break;
      }
    }
    LOG.info("Regexp: " + result);
    return result.toString();
  }

  public int getProcessedFiles() {
    return myProcessedFiles;
  }
}
