// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.util.Key;

import java.util.Set;

public final class ImportsHighlightUtil {
  public static final Key<Set<String>> IMPORTS_FROM_TEMPLATE = Key.create("IMPORT_FROM_FILE_TEMPLATE");
}
