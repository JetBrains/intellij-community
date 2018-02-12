/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Maxim.Mossienko
 */
public class ByteSequenceDataExternalizer implements DataExternalizer<ByteArraySequence> {
  public static final ByteSequenceDataExternalizer INSTANCE = new ByteSequenceDataExternalizer();

  @Override
  public void save(@NotNull DataOutput out, ByteArraySequence value) throws IOException {
    out.write(value.getBytes(), value.getOffset(), value.getLength()); // todo fix double copying
  }

  @Override
  public ByteArraySequence read(@NotNull DataInput in) throws IOException {
    byte[] buf = new byte[((InputStream)in).available()]; // todo fix double copying
    in.readFully(buf);
    return new ByteArraySequence(buf);
  }
}
