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

package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author nik
 */
public class RemoteRepositoryInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.ui.libraries.RemoteRepositoryInfo");
  private final String myId;
  private final String myPresentableName;
  private final String[] myMirrors;

  public RemoteRepositoryInfo(@NotNull @NonNls String id, final @NotNull @Nls String presentableName, final @NotNull @NonNls String[] mirrors) {
    myId = id;
    LOG.assertTrue(mirrors.length > 0);
    myPresentableName = presentableName;
    myMirrors = mirrors;
  }

  public String getId() {
    return myId;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public String[] getMirrors() {
    return myMirrors;
  }

  public String getDefaultMirror() {
    return myMirrors[0];
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RemoteRepositoryInfo that = (RemoteRepositoryInfo)o;
    return myId.equals(that.myId);

  }

  public int hashCode() {
    return myId.hashCode();
  }
}
