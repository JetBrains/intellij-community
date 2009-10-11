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
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class GroupingElement extends ErrorTreeElement {
  private final String[] myText;
  private final Object myData;
  private final VirtualFile myFile;

  public GroupingElement(String name, Object data, VirtualFile file) {
    super(ErrorTreeElementKind.GENERIC);
    myText = new String[] {name};
    myData = data;
    myFile = file;
  }

  public Object getData() {
    return myData;
  }

  public String[] getText() {
    return myText;
  }

  public String getName() {
    return myText[0];
  }

  public String getExportTextPrefix() {
    return "";
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
