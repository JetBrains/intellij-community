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
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:53:00 PM
 */
package com.intellij.compiler.impl;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public class TimestampCache extends StateCache <Long> {
  public TimestampCache(File storeDirectory) throws IOException {
    super(new File(storeDirectory, "timestamps"));
  }

  public void update(String url, @NotNull Long state) throws IOException {
    super.update(url, state);
  }

  public Long read(DataInput stream) throws IOException {
    return stream.readLong();
  }

  public void write(Long aLong, DataOutput out) throws IOException {
    out.writeLong(aLong.longValue());
  }
}
