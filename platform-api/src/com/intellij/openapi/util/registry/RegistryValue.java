package com.intellij.openapi.util.registry;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;

import java.util.concurrent.CopyOnWriteArraySet;

public class RegistryValue {

  private Registry myRegistry;
  private String myKey;

  private CopyOnWriteArraySet<RegistryValueListener> myListeners = new CopyOnWriteArraySet<RegistryValueListener>();

  private boolean myChangedSinceStart;

  private String myCachedValue;

  RegistryValue(Registry registry, String key) {
    myRegistry = registry;
    myKey = key;
  }

  public String getKey() {
    return myKey;
  }


  public String asString() {
    final String value = get(myKey, null);
    assert value != null : myKey;
    return value;
  }

  public boolean asBoolean() {
    return Boolean.valueOf(get(myKey, "false"));
  }

  public String getDescription() {
    return get(myKey + ".description", "");
  }

  public boolean isRestartRequired() {
    return Boolean.valueOf(get(myKey + ".restartRequired", "false"));
  }

  public boolean isChangedFromDefault() {
    return !getBundleValue(myKey).equals(asString());
  }

  private String get(String key, String defaultValue) {
    if (myCachedValue == null) {
      myCachedValue = _get(key, defaultValue);
    }

    return myCachedValue;
  }

  private String _get(String key, String defaultValue) {
    final String userValue = myRegistry.getUserProperties().get(key);
    if (userValue == null) {
      final String bundleValue = getBundleValue(key);
      if (bundleValue != null) {
        return bundleValue;
      } else {
        return defaultValue;
      }
    } else {
      return userValue;
    }
  }

  private String getBundleValue(String key) {
    return myRegistry.getBundle().getString(key);
  }

  public void setValue(String value) {
    myCachedValue = null;

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
    setValue(getBundleValue(myKey));
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
}