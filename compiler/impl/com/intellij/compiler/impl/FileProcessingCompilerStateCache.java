/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:17:50 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.compiler.ValidityStateFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class FileProcessingCompilerStateCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.FileProcessingCompilerStateCache");
  private final StateCache<MyState> myCache;

  public FileProcessingCompilerStateCache(String storeDirectory, String idPrefix, final ValidityStateFactory stateFactory, final StringInterner interner) {
    myCache = new StateCache<MyState>(storeDirectory + File.separator + idPrefix + "_timestamp.dat", interner) {
      public MyState read(DataInputStream stream) throws IOException {
        return new MyState(stream.readLong(), stateFactory.createValidityState(stream));
      }

      public void write(MyState state, DataOutputStream stream) throws IOException {
        stream.writeLong(state.getTimestamp());
        final ValidityState extState = state.getExtState();
        if (extState != null) {
          extState.save(stream);
        }
      }
    };
  }

  public void update(VirtualFile sourceFile, ValidityState extState) {
    myCache.update(sourceFile.getUrl(), new MyState(sourceFile.getTimeStamp(), extState));
  }

  public void remove(String url) {
    myCache.remove(url);
  }

  public long getTimestamp(String url) {
    final Serializable savedState = myCache.getState(url);
    if (savedState != null) {
      LOG.assertTrue(savedState instanceof MyState);
    }
    MyState state = (MyState)savedState;
    return (state != null)? state.getTimestamp() : -1L;
  }

  public ValidityState getExtState(String url) {
    MyState state = myCache.getState(url);
    return (state != null)? state.getExtState() : null;
  }

  public void save() {
    myCache.save();
  }

  public String[] getUrls() {
    return myCache.getUrls();
  }

  public boolean wipe() {
    return myCache.wipe();
  }

  public boolean isDirty() {
    return myCache.isDirty();
  }

  private static class MyState implements Serializable {
    private final long myTimestamp;
    private final ValidityState myExtState;

    public MyState(long timestamp, @Nullable ValidityState extState) {
      myTimestamp = timestamp;
      myExtState = extState;
    }

    public long getTimestamp() {
      return myTimestamp;
    }

    public @Nullable ValidityState getExtState() {
      return myExtState;
    }
  }

}
