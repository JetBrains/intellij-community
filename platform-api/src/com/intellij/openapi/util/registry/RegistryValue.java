package com.intellij.openapi.util.registry;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.MissingResourceException;

public class RegistryValue {

  private Registry myRegistry;
  private String myKey;

  private CopyOnWriteArraySet<RegistryValueListener> myListeners = new CopyOnWriteArraySet<RegistryValueListener>();

  private boolean myChangedSinceStart;

  private String myStringCachedValue;
  private Integer myIntCachedValue;
  private Boolean myBooleanCachedValue;

  RegistryValue(Registry registry, String key) {
    myRegistry = registry;
    myKey = key;
  }

  public String getKey() {
    return myKey;
  }


  public String asString() {
    final String value = get(myKey, null, true);
    assert value != null : myKey;
    return value;
  }

  public boolean asBoolean() {
    if (myBooleanCachedValue == null) {
      myBooleanCachedValue = Boolean.valueOf(get(myKey, "false", true));
    }

    return myBooleanCachedValue.booleanValue();
  }

  public int asInteger() {
    if (myIntCachedValue == null) {
      myIntCachedValue = Integer.valueOf(get(myKey, "0", true));
    }

    return myIntCachedValue.intValue();
  }

  public String getDescription() {
    return get(myKey + ".description", "", false);
  }

  public boolean isRestartRequired() {
    return Boolean.valueOf(get(myKey + ".restartRequired", "false", false));
  }

  public boolean isChangedFromDefault() {
    return !getBundleValue(myKey, true).equals(asString());
  }

  private String get(String key, String defaultValue, boolean isValue) {
    if (isValue) {
      if (myStringCachedValue == null) {
        myStringCachedValue = _get(key, defaultValue, isValue);
      }

      return myStringCachedValue;
    } else {
      return _get(key, defaultValue, isValue);
    }
  }

  private String _get(String key, String defaultValue, boolean mustExistInBundle) {
    final String userValue = myRegistry.getUserProperties().get(key);
    if (userValue == null) {
      final String bundleValue = getBundleValue(key, mustExistInBundle);
      if (bundleValue != null) {
        return bundleValue;
      } else {
        return defaultValue;
      }
    } else {
      return userValue;
    }
  }

  private String getBundleValue(String key, boolean mustExist) {
    try {
      return myRegistry.getBundle().getString(key);
    }
    catch (MissingResourceException e) {
      if (mustExist) {
        throw e;
      }
    }

    return null;
  }

  public void setValue(String value) {
    resetCache();

    for (RegistryValueListener each : myListeners) {
      each.beforeValueChanged(this);
    }

    myRegistry.getUserProperties().put(myKey, value);

    for (RegistryValueListener each : myListeners) {
      each.afterValueChanged(this);
    }

    if (!isChangedFromDefault()) {
      myRegistry.getUserProperties().remove(myKey);
    }

    myChangedSinceStart = true;
  }

  public boolean isChangedSinceAppStart() {
    return myChangedSinceStart;
  }

  public void resetToDefault() {
    setValue(getBundleValue(myKey, true));
  }

  public void addListener(final RegistryValueListener listener, Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  @Override
  public String toString() {
    return myKey + "=" + asString();
  }

  void resetCache() {
    myStringCachedValue = null;
    myIntCachedValue = null;
    myBooleanCachedValue = null;
  }
}