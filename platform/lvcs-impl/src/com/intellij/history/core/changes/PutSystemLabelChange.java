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

package com.intellij.history.core.changes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class PutSystemLabelChange extends PutLabelChange {
  private final int myColor;

  public PutSystemLabelChange(long id, String name, String projectId, int color) {
    super(id, name, projectId);
    myColor = color;
  }

  public PutSystemLabelChange(DataInput in) throws IOException {
    super(in);
    myColor = in.readInt();
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    out.writeInt(myColor);
  }

  public int getColor() {
    return myColor;
  }
}
