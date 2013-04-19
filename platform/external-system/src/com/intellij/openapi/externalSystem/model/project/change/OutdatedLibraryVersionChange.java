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
package com.intellij.openapi.externalSystem.model.project.change;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;

/**
 * There is a possible case that particular library has different versions at external system and ide sides. Object of this class
 * encapsulates that information.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 12:21 PM
 */
public class OutdatedLibraryVersionChange extends AbstractExternalProjectStructureChange {

  @NotNull private final String    myBaseLibraryName;
  @NotNull private final LibraryId myLibraryId;
  @NotNull private final String    myExternalLibraryVersion;
  @NotNull private final LibraryId myIdeLibraryId;
  @NotNull private final String    myIdeLibraryVersion;

  public OutdatedLibraryVersionChange(@NotNull String baseLibraryName,
                                      @NotNull LibraryId libraryId,
                                      @NotNull String externalLibraryVersion,
                                      @NotNull LibraryId ideLibraryId,
                                      @NotNull String ideLibraryVersion)
  {
    myBaseLibraryName = baseLibraryName;
    myLibraryId = libraryId;
    myExternalLibraryVersion = externalLibraryVersion;
    myIdeLibraryId = ideLibraryId;
    myIdeLibraryVersion = ideLibraryVersion;
  }

  @NotNull
  public String getBaseLibraryName() {
    return myBaseLibraryName;
  }

  @NotNull
  public LibraryId getLibraryId() {
    return myLibraryId;
  }

  @NotNull
  public String getExternalLibraryVersion() {
    return myExternalLibraryVersion;
  }

  @NotNull
  public LibraryId getIdeLibraryId() {
    return myIdeLibraryId;
  }

  @NotNull
  public String getIdeLibraryVersion() {
    return myIdeLibraryVersion;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myBaseLibraryName.hashCode();
    result = 31 * result + myLibraryId.hashCode();
    result = 31 * result + myExternalLibraryVersion.hashCode();
    result = 31 * result + myIdeLibraryId.hashCode();
    result = 31 * result + myIdeLibraryVersion.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    OutdatedLibraryVersionChange change = (OutdatedLibraryVersionChange)o;

    if (!myBaseLibraryName.equals(change.myBaseLibraryName)) return false;
    if (!myLibraryId.equals(change.myLibraryId)) return false;
    if (!myExternalLibraryVersion.equals(change.myExternalLibraryVersion)) return false;
    if (!myIdeLibraryId.equals(change.myIdeLibraryId)) return false;
    if (!myIdeLibraryVersion.equals(change.myIdeLibraryVersion)) return false;

    return true;
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public String toString() {
    return String.format("'%s' library version change: '%s' -> '%s'", myBaseLibraryName, myIdeLibraryVersion, myExternalLibraryVersion);
  }
}