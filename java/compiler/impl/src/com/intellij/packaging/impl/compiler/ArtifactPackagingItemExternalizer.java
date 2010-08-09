/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
* @author nik
*/
public class ArtifactPackagingItemExternalizer implements DataExternalizer<ArtifactPackagingItemOutputState> {
  private byte[] myBuffer = IOUtil.allocReadWriteUTFBuffer();

  @Override
  public void save(DataOutput out, ArtifactPackagingItemOutputState value) throws IOException {
    out.writeInt(value.myDestinations.size());
    for (Pair<String, Long> pair : value.myDestinations) {
      IOUtil.writeUTFFast(myBuffer, out, pair.getFirst());
      out.writeLong(pair.getSecond());
    }
  }

  @Override
  public ArtifactPackagingItemOutputState read(DataInput in) throws IOException {
    int size = in.readInt();
    SmartList<Pair<String, Long>> destinations = new SmartList<Pair<String, Long>>();
    while (size-- > 0) {
      String path = IOUtil.readUTFFast(myBuffer, in);
      long outputTimestamp = in.readLong();
      destinations.add(Pair.create(path, outputTimestamp));
    }
    return new ArtifactPackagingItemOutputState(destinations);
  }
}
