/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ExtensionFileNameMatcher implements FileNameMatcher {
  private String myExtension;
                         
  public ExtensionFileNameMatcher(@NotNull @NonNls String extension) {
    myExtension = extension.toLowerCase();
  }

  public boolean accept(@NotNull @NonNls String fileName) {
    return fileName.regionMatches(true, fileName.length() - myExtension.length() - 1, "." + myExtension, 0, myExtension.length() + 1);
  }

  @NonNls
  @NotNull
  public String getPresentableString() {
    return "*." + myExtension;
  }

  public String getExtension() {
    return myExtension;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExtensionFileNameMatcher that = (ExtensionFileNameMatcher)o;

    if (!myExtension.equals(that.myExtension)) return false;

    return true;
  }

  public int hashCode() {
    return myExtension.hashCode();
  }
}
