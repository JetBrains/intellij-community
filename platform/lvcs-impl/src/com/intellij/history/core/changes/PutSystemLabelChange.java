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

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class PutSystemLabelChange extends PutLabelChange {
  private final int myColor;

  public PutSystemLabelChange(String name, int color, long timestamp) {
    super(name, timestamp);
    myColor = color;
  }

  public PutSystemLabelChange(Stream s) throws IOException {
    super(s);
    myColor = s.readInteger();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myColor);
  }

  @Override
  public boolean isSystemLabel() {
    return true;
  }

  public int getColor() {
    return myColor;
  }
}
