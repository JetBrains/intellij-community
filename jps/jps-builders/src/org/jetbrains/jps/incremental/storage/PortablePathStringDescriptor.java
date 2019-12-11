// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;

public class PortablePathStringDescriptor extends EnumeratorStringDescriptor {
  @Override
  public int getHashCode(String value) {
    // On case insensitive OS hash calculated from value converted to lower case
    return StringUtil.isEmpty(value) ? 0 : FileUtil.toCanonicalPath(value).hashCode();
  }

  @Override
  public boolean isEqual(String val1, String val2) {
    // On case insensitive OS hash calculated from path converted to lower case
    if (val1 == val2) return true;
    if (val1 == null || val2 == null) return false;

    String path1 = FileUtil.toCanonicalPath(val1);
    String path2 = FileUtil.toCanonicalPath(val2);
    return path1.equals(path2);
  }
}
