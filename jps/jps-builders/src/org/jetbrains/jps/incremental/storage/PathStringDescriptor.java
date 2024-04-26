// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.io.EnumeratorStringDescriptor;

import static org.jetbrains.jps.incremental.storage.ProjectStamps.PORTABLE_CACHES;

public final class PathStringDescriptor extends EnumeratorStringDescriptor {
  public static final PathStringDescriptor INSTANCE = new PathStringDescriptor();

  @Override
  public int getHashCode(String value) {
    if (!PORTABLE_CACHES) return FileUtil.pathHashCode(value);
    // On case insensitive OS hash calculated from value converted to lower case
    return StringUtil.isEmpty(value) ? 0 : FileUtil.toCanonicalPath(value).hashCode();

  }

  @Override
  public boolean isEqual(String val1, String val2) {
    if (!PORTABLE_CACHES) return FileUtil.pathsEqual(val1, val2);
    // On case insensitive OS hash calculated from path converted to lower case
    if (Strings.areSameInstance(val1, val2)) return true;
    if (val1 == null || val2 == null) return false;

    String path1 = FileUtil.toCanonicalPath(val1);
    String path2 = FileUtil.toCanonicalPath(val2);
    return path1.equals(path2);
  }
}
