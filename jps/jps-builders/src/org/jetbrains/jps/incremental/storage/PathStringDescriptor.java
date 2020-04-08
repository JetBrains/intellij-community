/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;

import static org.jetbrains.jps.incremental.storage.ProjectStamps.PORTABLE_CACHES;

public class PathStringDescriptor extends EnumeratorStringDescriptor {
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
    if (val1 == val2) return true;
    if (val1 == null || val2 == null) return false;

    String path1 = FileUtil.toCanonicalPath(val1);
    String path2 = FileUtil.toCanonicalPath(val2);
    return path1.equals(path2);
  }
}
