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
 */

package com.intellij.util.descriptors;

/**
 * @author nik
 */
public class CustomConfigFile {
  public static final CustomConfigFile[] EMPTY_ARRAY = new CustomConfigFile[0];
  private String myUrl;
  private String myOutputDirectoryPath;


  public CustomConfigFile(final String url, final String outputDirectoryPath) {
    myUrl = url;
    myOutputDirectoryPath = outputDirectoryPath;
  }


  public String getUrl() {
    return myUrl;
  }

  public String getOutputDirectoryPath() {
    return myOutputDirectoryPath;
  }
}
