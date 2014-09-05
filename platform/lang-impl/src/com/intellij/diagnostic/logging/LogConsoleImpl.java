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

package com.intellij.diagnostic.logging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsoleImpl extends LogConsoleBase {
  private final String myPath;
  @NotNull
  private final File myFile;
  @NotNull
  private final Charset myCharset;
  private long myOldLength = 0;

  /**
   * @deprecated use {@link #LogConsoleImpl(com.intellij.openapi.project.Project, java.io.File, java.nio.charset.Charset, long, String, boolean, com.intellij.psi.search.GlobalSearchScope)}
   */
  public LogConsoleImpl(Project project,
                        @NotNull File file,
                        @NotNull Charset charset,
                        long skippedContents,
                        @NotNull String title,
                        final boolean buildInActions) {
    this(project, file, charset, skippedContents, title, buildInActions, GlobalSearchScope.allScope(project));
  }

  public LogConsoleImpl(Project project,
                        @NotNull File file,
                        @NotNull Charset charset,
                        long skippedContents,
                        @NotNull String title,
                        final boolean buildInActions,
                        final GlobalSearchScope searchScope) {
    super(project, getReader(file, charset, skippedContents), title, buildInActions, new DefaultLogFilterModel(project),
          searchScope);
    myPath = file.getAbsolutePath();
    myFile = file;
    myCharset = charset;
  }

  @Nullable
  private static Reader getReader(@NotNull File file, @NotNull Charset charset, long skippedContents) {
    try {
      try {
        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
        FileInputStream inputStream = new FileInputStream(file);
        //do not skip forward
        if (file.length() >= skippedContents) {
          long skipped = 0;
          while (skipped < skippedContents) {
            skipped += inputStream.skip(skippedContents - skipped);
          }
        }
        return new BufferedReader(new InputStreamReader(inputStream, charset));
      }
      catch (FileNotFoundException ignored) {
        if (FileUtilRt.createIfNotExists(file)) {
          return new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
        }
        return null;
      }
    }
    catch (Throwable ignored) {
      return null;
    }
  }

  @Override
  @Nullable
  public String getTooltip() {
    return myPath;
  }

  public String getPath() {
    return myPath;
  }

  @Nullable
  @Override
  protected BufferedReader updateReaderIfNeeded(@Nullable BufferedReader reader) throws IOException {
    if (reader == null) {
      return null;
    }

    long length = myFile.length();
    if (length < myOldLength) {
      reader.close();
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(myFile), myCharset));
    }
    myOldLength = length;
    return reader;
  }
}
