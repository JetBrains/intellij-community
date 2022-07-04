// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import java.util.Locale;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Utility class for working with Preferences
 */
public final class Prefs {
  public static void put(String key, String value) {
    getPreferences(key).put(getNodeKey(key), value);
  }

  public static String get(String key, String def) {
    return getPreferences(key).get(getNodeKey(key), def);
  }

  public static void putInt(String key, int value) {
    getPreferences(key).putInt(getNodeKey(key), value);
  }

  public static int getInt(String key, int def) {
    return getPreferences(key).getInt(getNodeKey(key), def);
  }

  public static void putLong(String key, long value) {
    getPreferences(key).putLong(getNodeKey(key), value);
  }

  public static long getLong(String key, long def) {
    return getPreferences(key).getLong(getNodeKey(key), def);
  }

  public static void putBoolean(String key, boolean value) {
    getPreferences(key).putBoolean(getNodeKey(key), value);
  }

  public static boolean getBoolean(String key, boolean def) {
    return getPreferences(key).getBoolean(getNodeKey(key), def);
  }

  public static void putFloat(String key, float value) {
    getPreferences(key).putFloat(getNodeKey(key), value);
  }

  public static float getFloat(String key, float def) {
    return getPreferences(key).getFloat(getNodeKey(key), def);
  }

  public static void putDouble(String key, double value) {
    getPreferences(key).putDouble(getNodeKey(key), value);
  }

  public static double getDouble(String key, double def) {
    return getPreferences(key).getDouble(getNodeKey(key), def);
  }

  public static void putByteArray(String key, byte[] value) {
    getPreferences(key).putByteArray(getNodeKey(key), value);
  }

  public static byte[] getByteArray(String key, byte[] def) {
    return getPreferences(key).getByteArray(getNodeKey(key), def);
  }

  public static void remove(String key) {
    getPreferences(key).remove(getNodeKey(key));
  }

  public static void flush(String key) throws BackingStoreException {
    getPreferences(key).flush();
  }

  private static String getNodeKey(String key) {
    int dotIndex = key.lastIndexOf('.');
    return (dotIndex >= 0 ? key.substring(dotIndex + 1) : key).toLowerCase(Locale.ENGLISH);
  }

  private static Preferences getPreferences(String key) {
    Preferences prefs = Preferences.userRoot();
    final int dotIndex = key.lastIndexOf('.');
    if (dotIndex > 0) {
      StringTokenizer tokenizer = new StringTokenizer(key.substring(0, dotIndex), ".", false);
      while (tokenizer.hasMoreElements()) {
        String str = tokenizer.nextToken();
        prefs = prefs.node(str == null ? null : str.toLowerCase(Locale.ENGLISH));
      }
    }
    return prefs;
  }
}
