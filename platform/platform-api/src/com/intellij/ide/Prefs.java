package com.intellij.ide;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.text.StringTokenizer;

import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Utility class for working with Preferences
 * todo: in one year the migration code could be removed
 * @author Eugene Zhuravlev
 */
public class Prefs {

  public static void put(String key, String value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).put(getNodeKey(key), value);
  }

  public static String get(String key, String def) {
    migrate(key, def, Preferences::get, Preferences::put);
    return getPreferences(key).get(getNodeKey(key), def);
  }

  public static void putInt(String key, int value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putInt(getNodeKey(key), value);
  }

  public static int getInt(String key, int def) {
    migrate(key, def, Preferences::getInt, Preferences::putInt);
    return getPreferences(key).getInt(getNodeKey(key), def);
  }

  public static void putLong(String key, long value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putLong(getNodeKey(key), value);
  }

  public static long getLong(String key, long def) {
    migrate(key, def, Preferences::getLong, Preferences::putLong);
    return getPreferences(key).getLong(getNodeKey(key), def);
  }

  public static void putBoolean(String key, boolean value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putBoolean(getNodeKey(key), value);
  }

  public static boolean getBoolean(String key, boolean def) {
    migrate(key, def, Preferences::getBoolean, Preferences::putBoolean);
    return getPreferences(key).getBoolean(getNodeKey(key), def);
  }

  public static void putFloat(String key, float value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putFloat(getNodeKey(key), value);
  }

  public static float getFloat(String key, float def) {
    migrate(key, def, Preferences::getFloat, Preferences::putFloat);
    return getPreferences(key).getFloat(getNodeKey(key), def);
  }

  public static void putDouble(String key, double value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putDouble(getNodeKey(key), value);
  }

  public static double getDouble(String key, double def) {
    migrate(key, def, Preferences::getDouble, Preferences::putDouble);
    return getPreferences(key).getDouble(getNodeKey(key), def);
  }

  public static void putByteArray(String key, byte[] value) {
    Preferences.userRoot().remove(key); // remove from old location
    getPreferences(key).putByteArray(getNodeKey(key), value);
  }

  public static byte[] getByteArray(String key, byte[] def) {
    migrate(key, def, Preferences::getByteArray, Preferences::putByteArray);
    return getPreferences(key).getByteArray(getNodeKey(key), def);
  }

  public static void remove(String key) {
    getPreferences(key).remove(getNodeKey(key));
  }

  private static String getNodeKey(String key) {
    final int dotIndex = key.lastIndexOf('.');
    return (dotIndex >= 0 ? key.substring(dotIndex + 1) : key).toLowerCase(Locale.US);
  }

  private static Preferences getPreferences(String key) {
    Preferences prefs = Preferences.userRoot();
    final int dotIndex = key.lastIndexOf('.');
    if (dotIndex > 0) {
      final StringTokenizer tokenizer = new StringTokenizer(key.substring(0, dotIndex), ".", false);
      while (tokenizer.hasMoreElements()) {
        prefs = prefs.node(tokenizer.nextElement().toLowerCase(Locale.US));
      }
    }
    return prefs;
  }

  private interface Getter<T> {
    T get(Preferences prefs, String key, T def);
  }

  private interface Setter<T> {
    void set(Preferences prefs, String key, T value);
  }

  private static <T> void migrate(String key, T def, Getter<T> getter, Setter<T> setter) {
    // rewrite from old location into the new one
    final Preferences prefs = Preferences.userRoot();
    final T val = getter.get(prefs, key, def);
    if (!Comparing.equal(val, def)) {
      setter.set(getPreferences(key), getNodeKey(key), val);
      prefs.remove(key);
    }
  }

}
