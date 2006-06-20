/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:53:00 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.StringInterner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class TimestampCache extends StateCache <Long> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TimestampCache");
  public TimestampCache(String storeDirectory, String idPrefix, final StringInterner interner) {
    super(storeDirectory + File.separator + idPrefix + "_timestamp.dat", interner);
  }

  public void update(String url, Long state) {
    LOG.assertTrue(state != null);
    if (LOG.isDebugEnabled()) {
      LOG.debug("TimestampCache.update: " + url + "; " + state);
    }
    super.update(url, state);
  }

  public Long read(DataInputStream stream) throws IOException {
    return new Long(stream.readLong());
  }

  public void write(Long aLong, DataOutputStream stream) throws IOException {
    stream.writeLong(aLong.longValue());
  }

  protected boolean load() {
    final boolean notInitialized = (myMap == null);
    if (LOG.isDebugEnabled() && notInitialized) {
      LOG.debug("TimestampCache.load");
    }
    try {
      return super.load();
    }
    finally {
      if (LOG.isDebugEnabled() && notInitialized) {
        LOG.debug("TimestampCache.loaded: " + (myMap != null? myMap.size() + " items" : "empty map"));
      }
    }
  }

  public void save() {
    if (LOG.isDebugEnabled() && myMap != null) {
      LOG.debug("TimestampCache.save");
    }
    super.save();
  }

  public boolean wipe() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("TimestampCache.wipe");
    }
    return super.wipe();
  }

  public void remove(final String url) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("TimestampCache.remove: " + url);
    }
    super.remove(url);
  }

}
