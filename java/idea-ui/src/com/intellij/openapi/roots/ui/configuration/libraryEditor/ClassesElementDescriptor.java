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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;

class ClassesElementDescriptor extends NodeDescriptor<ClassesElement> {
    private final ClassesElement myElement;
    public static final Icon ICON = IconLoader.getIcon("/nodes/compiledClassesFolder.png");

    public ClassesElementDescriptor(NodeDescriptor parentDescriptor, ClassesElement element) {
      super(null, parentDescriptor);
      myElement = element;
      myOpenIcon = myClosedIcon = ICON;
    }

    public boolean update() {
      myName = ProjectBundle.message("library.classes.node");
      return false;
    }

    public ClassesElement getElement() {
      return myElement;
    }
  }
