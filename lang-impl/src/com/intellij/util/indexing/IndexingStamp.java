package com.intellij.util.indexing;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectLongProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private IndexingStamp() {
  }

  private static class Timestamps {
    private static FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 1);
    private final TObjectLongHashMap<ID<?, ?>> myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(20);

    public void readFromStream(DataInputStream stream) throws IOException {
      int count = DataInputOutputUtil.readINT(stream);
      for (int i = 0; i < count; i++) {
        ID<?, ?> id = ID.findById(stream.readLong());
        long timestamp = stream.readLong();
        myIndexStamps.put(id, timestamp);
      }
    }

    public void writeToStream(final DataOutputStream stream) throws IOException {
      DataInputOutputUtil.writeINT(stream, myIndexStamps.size());
      myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
        public boolean execute(final ID<?, ?> id, final long timestamp) {
          try {
            stream.writeLong(id.getUniqueId());
            stream.writeLong(timestamp);
            return true;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }

    public long get(ID<?, ?> id) {
      return myIndexStamps.get(id);
    }

    public void set(ID<?, ?> id, long tmst) {
      myIndexStamps.put(id, tmst);
    }
  }

  private static final SLRUCache<VirtualFile, Timestamps> myTimestampsCache = new SLRUCache<VirtualFile, Timestamps>(5, 5) {
    @NotNull
    public Timestamps createValue(final VirtualFile key) {
      try {
        Timestamps result = new Timestamps();
        final DataInputStream stream = Timestamps.PERSISTENCE.readAttribute(key);
        if (stream != null) {
          result.readFromStream(stream);
        }
        return result;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void onDropFromCache(final VirtualFile key, final Timestamps value) {
      try {
        final DataOutputStream sink = Timestamps.PERSISTENCE.writeAttribute(key);
        value.writeToStream(sink);
        sink.close();
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public static boolean isFileIndexed(VirtualFile file, ID<?, ?> indexName, final long indexCreationStamp) {
    if (file instanceof NewVirtualFile && file.isValid()) {
      synchronized (myTimestampsCache) {
        return myTimestampsCache.get(file).get(indexName) == indexCreationStamp;
      }
    }

    return false;
  }

  public static void update(final VirtualFile file, final ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      if (file instanceof NewVirtualFile && file.isValid()) {
        synchronized (myTimestampsCache) {
          myTimestampsCache.get(file).set(indexName, indexCreationStamp);
        }
      }
    }
    catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
    }
  }

  public static void flushCache() {
    synchronized (myTimestampsCache) {
      myTimestampsCache.clear();
    }
  }

}
