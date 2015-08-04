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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.io.CaseInsensitiveEnumeratorStringDescriptor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ObjectObjectPersistentMultiMapletTest extends UsefulTestCase {
  private static final CollectionFactory<IntValueStreamable> COLLECTION_FACTORY = new CollectionFactory<IntValueStreamable>() {
    @Override
    public Collection<IntValueStreamable> create() {
      return new ArrayList<IntValueStreamable>();
    }
  };

  public void testReplaceWithEqualButNotSameKey() throws IOException {
    File file = FileUtil.createTempFile(getTestDirectoryName(), null);
    ObjectObjectPersistentMultiMaplet<String, IntValueStreamable> maplet =
      new ObjectObjectPersistentMultiMaplet<String, IntValueStreamable>(file, new CaseInsensitiveEnumeratorStringDescriptor(),
                                                                        new IntValueExternalizer(),
                                                                        COLLECTION_FACTORY);
    try {
      maplet.put("a", new IntValueStreamable(1));
      assertEquals(1, assertOneElement(maplet.get("a")).value);
      maplet.replace("A", Collections.singletonList(new IntValueStreamable(2)));
      assertEquals(2, assertOneElement(maplet.get("a")).value);
    } finally {
      maplet.close();
      IOUtil.deleteAllFilesStartingWith(file);
    }
  }

  private static class IntValueStreamable implements Streamable {
    public int value;

    public IntValueStreamable(int value) {
      this.value = value;
    }

    @Override
    public void toStream(DependencyContext context, PrintStream stream) {
    }
  }

  private static class IntValueExternalizer implements DataExternalizer<IntValueStreamable> {
    @Override
    public void save(@NotNull DataOutput out, IntValueStreamable value) throws IOException {
      out.writeInt(value.value);
    }

    @Override
    public IntValueStreamable read(@NotNull DataInput in) throws IOException {
      return new IntValueStreamable(in.readInt());
    }
  }
}