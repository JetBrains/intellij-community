// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public enum JsonMappingKind {
  File,
  Pattern,
  Directory;

  public @Nls String getDescription() {
    switch (this) {
      case File:
        return JsonBundle.message("schema.mapping.file");
      case Pattern:
        return JsonBundle.message("schema.mapping.pattern");
      case Directory:
        return JsonBundle.message("schema.mapping.directory");
    }
    return "";
  }

  public @Nls String getPrefix() {
    return StringUtil.capitalize(getDescription()) + ": ";
  }

  public Icon getIcon() {
    switch (this) {
      case File:
        return AllIcons.FileTypes.Any_type;
      case Pattern:
        return AllIcons.FileTypes.Unknown;
      case Directory:
        return AllIcons.Nodes.Folder;
    }
    return null;
  }
}
