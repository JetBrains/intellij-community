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

package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The password database. The internal component for {@link MasterKeyPasswordSafe}.
 */
@State(
  name = "PasswordDatabase",
  storages = {@Storage(
    id = "PasswordDatabase",
    file = "$APP_CONFIG$/security.xml")})
public class PasswordDatabase implements PersistentStateComponent<PasswordDatabase.State> {
  /**
   * The name of logger
   */
  private final static Logger LOG = Logger.getInstance(PasswordDatabase.class.getName());
  /**
   * The password database
   */
  private transient final Map<ByteArrayWrapper, byte[]> myDatabase = new HashMap<ByteArrayWrapper, byte[]>();
  /**
   * OS-specific information about master password
   */
  private transient byte[] myMasterPasswordInfo;

  /**
   * Clear the password database
   */
  public void clear() {
    synchronized (myDatabase) {
      myDatabase.clear();
    }
  }

  /**
   * @return true if the database is empty
   */
  public boolean isEmpty() {
    synchronized (myDatabase) {
      return myDatabase.isEmpty();
    }
  }

  /**
   * Put password in the database
   *
   * @param key   the encrypted key
   * @param value the encrypted value
   */
  public void put(byte[] key, byte[] value) {
    synchronized (myDatabase) {
      myDatabase.put(new ByteArrayWrapper(key), value);
    }
  }

  /**
   * Get all entries in the database
   *
   * @param copy the copy to use
   */
  public void copyTo(Map<ByteArrayWrapper, byte[]> copy) {
    synchronized (myDatabase) {
      copy.putAll(myDatabase);
    }
  }

  /**
   * Put all entries to the database
   *
   * @param copy the copy to use
   */
  public void putAll(Map<ByteArrayWrapper, byte[]> copy) {
    synchronized (myDatabase) {
      myDatabase.putAll(copy);
    }
  }


  /**
   * Get password from the database
   *
   * @param key the encrypted key
   * @return the encrypted value or null
   */
  public byte[] get(byte[] key) {
    synchronized (myDatabase) {
      return myDatabase.get(new ByteArrayWrapper(key));
    }

  }

  /**
   * Remove password from the database
   *
   * @param key the encrypted key
   */
  public void remove(byte[] key) {
    synchronized (myDatabase) {
      myDatabase.remove(new ByteArrayWrapper(key));
    }
  }

  /**
   * {@inheritDoc}
   */
  public State getState() {
    TreeMap<ByteArrayWrapper, byte[]> sorted;
    String pi;
    synchronized (myDatabase) {
      pi = toHex(myMasterPasswordInfo);
      sorted = new TreeMap<ByteArrayWrapper, byte[]>(myDatabase);
    }
    String[][] db = new String[2][sorted.size()];
    int i = 0;
    for (Map.Entry<ByteArrayWrapper, byte[]> e : sorted.entrySet()) {
      db[0][i] = toHex(e.getKey().unwrap());
      db[1][i] = toHex(e.getValue());
      i++;
    }
    State s = new State();
    s.PASSWORDS = db;
    s.MASTER_PASSWORD_INFO = pi;
    return s;
  }


  /**
   * Covert bytes to hex
   *
   * @param bytes bytes to convert
   * @return hex representation
   */
  @Nullable
  private static String toHex(byte[] bytes) {
    return bytes == null ? null : new String(Hex.encodeHex(bytes));
  }

  /**
   * Covert hex to bytes
   *
   * @param hex string to convert
   * @return bytes representation
   * @throws DecoderException if invalid data encountered
   */
  @Nullable
  private static byte[] fromHex(String hex) throws DecoderException {
    return hex == null ? null : Hex.decodeHex(hex.toCharArray());
  }

  /**
   * {@inheritDoc}
   */
  public void loadState(State state) {
    String[][] db = state.PASSWORDS;
    String pi = state.MASTER_PASSWORD_INFO;
    synchronized (myDatabase) {
      try {
        myMasterPasswordInfo = fromHex(pi);
        if (myMasterPasswordInfo == null) {
          myMasterPasswordInfo = new byte[0];
        }
      }
      catch (DecoderException e) {
        myMasterPasswordInfo = new byte[0];
      }
      myDatabase.clear();
      if (db[0].length != db[1].length) {
        LOG.warn("The password database is in inconsistent state, ignoring it: " + db[0].length + " != " + db[1].length);
      }
      int n = db[0].length;
      for (int i = 0; i < n; i++) {
        try {
          byte[] key = fromHex(db[0][i]);
          byte[] value = fromHex(db[1][i]);
          if (key != null && value != null) {
            myDatabase.put(new ByteArrayWrapper(key), value);
          }
        }
        catch (DecoderException e) {
          // skip the entry
        }
      }
    }
  }

  /**
   * @return the object over which database is synchronized
   */
  Object getDbLock() {
    return myDatabase;
  }

  /**
   * @return master password information
   */
  byte[] getPasswordInfo() {
    synchronized (myDatabase) {
      return myMasterPasswordInfo;
    }
  }

  /**
   * Set master password information
   *
   * @param bytes the bytes for the master password
   */
  public void setPasswordInfo(byte[] bytes) {
    synchronized (myDatabase) {
      myMasterPasswordInfo = bytes;
    }
  }

  /**
   * The state for passwords database
   */
  public static class State {
    /**
     * Information about master password (used in OS specific way)
     */
    public String MASTER_PASSWORD_INFO = "";
    /**
     * The password database
     */
    public String[][] PASSWORDS = new String[0][];
  }
}
