package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerDirectoryTimestamp");
  
  private static final Map<ID<?, ?>, FileAttribute> ourAttributes = new HashMap<ID<?, ?>, FileAttribute>();
  private static final Map<ID<?, ?>, Key<Long>> ourUserDataKeys = new HashMap<ID<?,?>, Key<Long>>();

  private IndexingStamp() {
  }

  public static boolean isFileIndexed(VirtualFile file, ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      if (!(file instanceof NewVirtualFile)) return false;
      if (!file.isValid()) {
        return false;
      }
      final Key<Long> userDataKey = getUserDataKey(indexName);
      Long cachedStamp = file.getUserData(userDataKey);

      if (cachedStamp == null) {
        final DataInputStream stream = getAttribute(indexName).readAttribute(file);
        if (stream != null) {
          try {
            cachedStamp = stream.readLong();
          }
          finally {
            stream.close();
          }
        }
        else {
          cachedStamp = -1L;
        }
        file.putUserData(userDataKey, cachedStamp);
      }
      
      return cachedStamp.longValue() == indexCreationStamp;
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
  }

  public static void update(final VirtualFile file, final ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      if (file instanceof NewVirtualFile && file.isValid()) {
        file.putUserData(getUserDataKey(indexName), indexCreationStamp);
        final DataOutputStream stream = getAttribute(indexName).writeAttribute(file);
        try {
          stream.writeLong(indexCreationStamp);
        }
        finally {
          stream.close();
        }
      }
    }
    catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private static FileAttribute getAttribute(ID<?, ?> indexName) {
    FileAttribute attrib = ourAttributes.get(indexName);
    if (attrib == null) {
      attrib = new FileAttribute("_indexing_stamp_" + indexName, 2);
      ourAttributes.put(indexName, attrib);
    }
    return attrib;
  }

  @NotNull
  private static Key<Long> getUserDataKey(ID<?, ?> indexName) {
    Key<Long> key = ourUserDataKeys.get(indexName);
    if (key == null) {
      key = Key.create("_indexing_stamp_" + indexName);
      ourUserDataKeys.put(indexName, key);
    }
    return key;
  }
  
}
