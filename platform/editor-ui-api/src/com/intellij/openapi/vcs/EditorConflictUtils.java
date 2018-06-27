// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Key;

public class EditorConflictUtils {
  public static final Key<String> ACTIVE_REVISION = Key.create("EditorConflict.ActiveRevision");
}
