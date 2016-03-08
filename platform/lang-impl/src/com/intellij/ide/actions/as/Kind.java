/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions.as;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

enum Kind {
  ANNOTATION("Annotation", PlatformIcons.ANNOTATION_TYPE_ICON),
  CLASS("Class", PlatformIcons.CLASS_ICON),
  ENUM("Enum", PlatformIcons.ENUM_ICON),
  INTERFACE("Interface", PlatformIcons.INTERFACE_ICON),
  SINGLETON("Singleton", JavaFileType.INSTANCE.getIcon());

  private final String myName;
  private final Icon myIcon;

  Kind(String name, Icon icon) {
    myName = name;
    myIcon = icon;
  }

  String getName() {
    return myName;
  }

  Icon getIcon() {
    return myIcon;
  }

  String getTemplateName() {
    return "AS" + myName;
  }

  static Kind valueOfText(String text) {
    for (Kind kind : values()) {
      if (kind.getTemplateName().equals(text)) {
        return kind;
      }
    }

    throw new IllegalArgumentException(text);
  }
}
