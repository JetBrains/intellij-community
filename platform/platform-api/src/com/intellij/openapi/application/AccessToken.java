package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import com.sun.xml.internal.fastinfoset.util.CharArray;

public abstract class AccessToken {
  protected void acquired() {
    String id = id();

    if (id != null) {
      final Thread thread = Thread.currentThread();
      thread.setName(thread.getName() + id);
    }
  }

  protected void released() {
    String id = id();

    if (id != null) {
      final Thread thread = Thread.currentThread();
      String name = thread.getName();
      name = StringUtil.replace(name, id, "");
      thread.setName(name);
    }
  }

  private String id() {
    Class aClass = getClass();
    String name = aClass.getName();
    while (name == null) {
      aClass = aClass.getSuperclass();
      name = aClass.getName();
    }

    name = name.substring(name.lastIndexOf('.') + 1);
    name = name.substring(name.lastIndexOf('$') + 1);
    if (!name.equals("AccessToken")) {
      return " [" + name+"]";
    }
    return null;
  }

  public abstract void finish();

  public static final AccessToken EMPTY_ACCESS_TOKEN = new AccessToken() {
    @Override
    public void finish() {}
  };
}
