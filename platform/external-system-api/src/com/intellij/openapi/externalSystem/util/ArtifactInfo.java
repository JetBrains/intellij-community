/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 1/16/13 6:26 PM
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2018.3")
@Deprecated
public class ArtifactInfo {

  @Nullable private final String myName;
  @Nullable private final String myGroup;
  @Nullable private final String myVersion;

  public ArtifactInfo(@Nullable String name, @Nullable String group, @Nullable String version) {
    assert name != null || group != null || version != null;
    myName = name;
    myGroup = group;
    myVersion = version;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  // Commented to apply to green code policy. Un-comment if required.
  
  //@Nullable
  //public String getGroup() {
  //  return myGroup;
  //}
  
  @Nullable
  public String getVersion() {
    return myVersion;
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myGroup != null ? myGroup.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArtifactInfo info = (ArtifactInfo)o;

    if (myGroup != null ? !myGroup.equals(info.myGroup) : info.myGroup != null) return false;
    if (myName != null ? !myName.equals(info.myName) : info.myName != null) return false;
    if (myVersion != null ? !myVersion.equals(info.myVersion) : info.myVersion != null) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s:%s:%s",
                         myName == null ? "<no-name>" : myName,
                         myGroup == null ? "<no-group>" : myGroup,
                         myVersion == null ? "<no-version>" : myVersion);
  }
}
