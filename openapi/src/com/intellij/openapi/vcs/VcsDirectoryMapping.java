/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 *
 */

package com.intellij.openapi.vcs;

/**
 * @author yole
 */
public class VcsDirectoryMapping {
  private String myDirectory;
  private String myVcs;

  public VcsDirectoryMapping() {
  }

  public VcsDirectoryMapping(final String directory, final String vcs) {
    myDirectory = directory;
    myVcs = vcs;
  }

  public String getDirectory() {
    return myDirectory;
  }

  public String getVcs() {
    return myVcs;
  }

  public void setVcs(final String vcs) {
    myVcs = vcs;
  }

  public void setDirectory(final String directory) {
    myDirectory = directory;
  }
}