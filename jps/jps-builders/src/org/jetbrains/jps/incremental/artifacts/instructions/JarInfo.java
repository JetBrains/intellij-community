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

package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JarInfo {
  private final List<Pair<String, Object>> myContent;
  private final DestinationInfo myDestination;

  public JarInfo(@NotNull DestinationInfo destination) {
    myDestination = destination;
    myContent = new ArrayList<Pair<String, Object>>();
  }

  public void addContent(String pathInJar, ArtifactRootDescriptor descriptor) {
    myContent.add(Pair.create(pathInJar, (Object)descriptor));
  }

  public void addJar(String pathInJar, JarInfo jarInfo) {
    myContent.add(Pair.create(pathInJar, (Object)jarInfo));
  }

  public List<Pair<String, Object>> getContent() {
    return myContent;
  }

  public DestinationInfo getDestination() {
    return myDestination;
  }

  public String getPresentableDestination() {
    return myDestination.getOutputPath();
  }
}
