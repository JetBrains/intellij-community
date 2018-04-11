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
package org.jetbrains.jps.model.serialization.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsOrderRootType;

/**
 * @author nik
 */
public class JpsLibraryRootTypeSerializer implements Comparable<JpsLibraryRootTypeSerializer> {
  private final String myTypeId;
  private final JpsOrderRootType myType;
  private final boolean myWriteIfEmpty;

  public JpsLibraryRootTypeSerializer(@NotNull String typeId, @NotNull JpsOrderRootType type, boolean writeIfEmpty) {
    myTypeId = typeId;
    myType = type;
    myWriteIfEmpty = writeIfEmpty;
  }

  public boolean isWriteIfEmpty() {
    return myWriteIfEmpty;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public JpsOrderRootType getType() {
    return myType;
  }

  @Override
  public int compareTo(JpsLibraryRootTypeSerializer o) {
    return myTypeId.compareTo(o.myTypeId);
  }
}
