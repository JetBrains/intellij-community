package com.intellij.mock;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

public class MockComponentManager extends UserDataHolderBase implements ComponentManager {
  private final MessageBus myMessageBus = MessageBusFactory.newMessageBus(this);

  private final Map<Class, Object> myComponents = new HashMap<Class, Object>();

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> void addComponent(Class<T> interfaceClass, T instance) {
    myComponents.put(interfaceClass, instance);
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return (T)myComponents.get(interfaceClass);
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return getComponent(interfaceClass) != null ? getComponent(interfaceClass) : null;
  }

  @NotNull
  public Class[] getComponentInterfaces() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  @NotNull
  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    return (T[])Array.newInstance(baseInterfaceClass, 0);
  }

  @NotNull
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("Method getPicoContainer is not supported in " + getClass());
  }

  public MessageBus getMessageBus() {
    return myMessageBus;
  }

  public boolean isDisposed() {
    return false;
  }

  public void dispose() {
  }
}
