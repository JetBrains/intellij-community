// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.navigation;

import com.intellij.json.JsonBundle;

public enum JsonQualifiedNameKind {
  Qualified,
  JsonPointer;

  @Override
  public String toString() {
    return switch (this) {
      case Qualified -> JsonBundle.message("qualified.name.qualified");
      case JsonPointer -> JsonBundle.message("qualified.name.pointer");
    };
  }
}
