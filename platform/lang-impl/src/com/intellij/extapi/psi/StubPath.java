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

/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.psi.tree.IElementType;

public class StubPath {
  private final StubPath myParentPath;
  private final String myId;
  private final IElementType myType;


  public StubPath(final StubPath parentPath, final String id, final IElementType type) {
    myParentPath = parentPath;
    myId = id;
    myType = type;
  }

  public StubPath getParentPath() {
    return myParentPath;
  }


  public String getId() {
    return myId;
  }

  public IElementType getType() {
    return myType;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof StubPath)) return false;

    final StubPath stubPath = (StubPath)o;

    if (!myId.equals(stubPath.myId)) return false;
    if (myParentPath != null ? !myParentPath.equals(stubPath.myParentPath) : stubPath.myParentPath != null) return false;
    if (!myType.equals(stubPath.myType)) return false;

    return true;
  }

  public int hashCode() {
    int result = (myParentPath != null ? myParentPath.hashCode() : 0);
    result = 31 * result + myId.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }

  public String toString() {
    return new StringBuilder().
        append(myParentPath != null ? myParentPath.toString() : "").
        append("::(").
        append(myType.toString()).
        append(":").
        append(myId).
        append(")").toString();
  }
}
