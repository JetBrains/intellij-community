// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class JpsPersistentHashMap<Key, Value> extends PersistentHashMap<Key, Value> {
  public JpsPersistentHashMap(@NotNull File file,
                              @NotNull KeyDescriptor<Key> keyDescriptor,
                              @NotNull DataExternalizer<Value> valueExternalizer)
    throws IOException {
    super(file, keyDescriptor, valueExternalizer);
  }

  /**
   * This method is used to append value directly into the chunk without saving to cache.
   * It can be used in case of non changed appended data like in JSP. The main goal for now
   * is to avoid binary data changes in saving during JPS builds because of flushing cache.
   */
  public final void appendDataWithoutCache(Key key, Value value) throws IOException {
    synchronized (myEnumerator) {
      try {
        final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
        AppendStream appenderStream = ourFlyweightAppenderStream.getValue();
        appenderStream.setOut(bytes);
        myValueExternalizer.save(appenderStream, value);
        appenderStream.setOut(null);
        appendDataWithoutCache(key, bytes);
      }
      catch (IOException ex) {
        markCorrupted();
        throw ex;
      }
    }
  }

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(null);
    }

    private void setOut(BufferExposingByteArrayOutputStream stream) {
      out = stream;
    }
  }

  private static final ThreadLocalCachedValue<AppendStream> ourFlyweightAppenderStream = new ThreadLocalCachedValue<AppendStream>() {
    @NotNull
    @Override
    protected AppendStream create() {
      return new AppendStream();
    }
  };
}
