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
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.roots.libraries.doc.DocUrlChooser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Note: for languages other than Java use com.intellij.openapi.roots.libraries.doc.DocUrlChooser.
 * @author MYakovlev
 * Date: Oct 29, 2002
 * Time: 8:47:43 PM
 */
public class Util{

  @Nullable
  public static VirtualFile showSpecifyJavadocUrlDialog(JComponent parent) {
    return showSpecifyJavadocUrlDialog(parent, "");
  }

  @Nullable
  public static VirtualFile showSpecifyJavadocUrlDialog(JComponent parent, String initialValue){
    return DocUrlChooser.showSpecifyDocUrlDialog(parent, initialValue);
  }


}
