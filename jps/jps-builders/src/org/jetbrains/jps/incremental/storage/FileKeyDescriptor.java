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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
* @author Eugene Zhuravlev
*         Date: 9/10/12
*/
public final class FileKeyDescriptor implements KeyDescriptor<File> {
  private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

  public void save(DataOutput out, File value) throws IOException {
    IOUtil.writeUTFFast(buffer, out, value.getPath());
  }

  public File read(DataInput in) throws IOException {
    return new File(IOUtil.readUTFFast(buffer, in));
  }

  public int getHashCode(File value) {
    return FileUtil.fileHashCode(value);
  }

  public boolean isEqual(File val1, File val2) {
    return FileUtil.filesEqual(val1, val2);
  }
}
