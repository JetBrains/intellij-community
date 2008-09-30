/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;

import javax.swing.*;
import java.lang.reflect.Method;

public class IconDeferrerImpl extends IconDeferrer {
  private final Object LOCK = new Object();
  private final SLRUMap<Object, Icon> myIconsCache = new SLRUMap<Object, Icon>(100, 100);

  public IconDeferrerImpl(MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.setDefaultHandler(new MessageHandler() {
      public void handle(final Method event, final Object... params) {
        myIconsCache.clear();
      }
    });

    connection.subscribe(ProjectTopics.MODIFICATION_TRACKER);
    connection.subscribe(VirtualFileManager.VFS_CHANGES);
  }

  public <T> Icon defer(final Icon base, final T param, final Function<T, Icon> f) {
    synchronized (LOCK) {
      Icon result = myIconsCache.get(param);
      if (result == null) {
        result = new DeferredIcon<T>(base, param, f);
        myIconsCache.put(param, result);
      }

      return result;
    }
  }
}