/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@Tag("artifacts")
public class ArtifactManagerState {
  private List<ArtifactState> myArtifacts = new ArrayList<ArtifactState>();

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<ArtifactState> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<ArtifactState> artifacts) {
    myArtifacts = artifacts;
  }
}
