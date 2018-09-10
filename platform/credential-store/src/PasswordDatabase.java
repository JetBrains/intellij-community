// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore;

import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Deprecated
@State(name = "PasswordDatabase", storages = @Storage(value = "security.xml", roamingType = RoamingType.DISABLED, deprecated = true))
class PasswordDatabase implements PersistentStateComponent<PasswordDatabase.State> {
  private final static Logger LOG = Logger.getInstance(PasswordDatabase.class.getName());

  public transient final Map<ByteArrayWrapper, byte[]> myDatabase = new HashMap<>();
  public transient byte[] myMasterPassword;

  @Override
  public State getState() {
    return new State();
  }

  @Nullable
  private static byte[] fromHex(@Nullable String hex) {
    return hex == null ? null : StringUtil.parseHexString(hex);
  }

  @Override
  public void loadState(@NotNull State state) {
    String[][] db = state.PASSWORDS;
    String pi = state.MASTER_PASSWORD_INFO;
    try {
      myMasterPassword = fromHex(pi);
    }
    catch (Exception e) {
      myMasterPassword = null;
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
      catch (Exception ignored) {
      }
    }
  }

  /**
   * The state for passwords database
   */
  public static class State {
    /**
     * Information about master password (used in OS specific way)
     */
    public String MASTER_PASSWORD_INFO;
    /**
     * The password database
     */
    public String[][] PASSWORDS = new String[2][];
  }
}
