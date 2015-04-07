/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.compiler.instrumentation;

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Label;

import java.io.IOException;
import java.io.InputStream;

public class FailSafeClassReader extends ClassReader {
  public FailSafeClassReader(byte[] b) {
    super(b);
  }

  public FailSafeClassReader(byte[] b, int off, int len) {
    super(b, off, len);
  }

  public FailSafeClassReader(InputStream is) throws IOException {
    super(is);
  }

  public FailSafeClassReader(String name) throws IOException {
    super(name);
  }

  @Override
  protected Label readLabel(int offset, Label[] labels) {
    // attempt to workaround javac bug: 
    // annotation table from original method is duplicated for synthetic bridge methods. 
    // All offsets in the duplicated table is taken from original annotations table and obviously are not relevant for the bridge method
    return offset < labels.length && offset >= 0? super.readLabel(offset, labels) : null;
  }
}
