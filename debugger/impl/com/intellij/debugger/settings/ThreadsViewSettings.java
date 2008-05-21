package com.intellij.debugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name="ThreadsViewSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/debugger.threadsview.xml"
    )}
)
public class ThreadsViewSettings implements PersistentStateComponent<ThreadsViewSettings> {
  public boolean SHOW_THREAD_GROUPS = false;
  public boolean SHOW_LINE_NUMBER = true;
  public boolean SHOW_CLASS_NAME = true;
  public boolean SHOW_SOURCE_NAME = false;
  public boolean SHOW_SYNTHETIC_FRAMES = true;
  public boolean SHOW_CURRENT_THREAD = true;

  public static ThreadsViewSettings getInstance() {
    return ServiceManager.getService(ThreadsViewSettings.class);
 }

  public ThreadsViewSettings getState() {
    return this;
  }

  public void loadState(final ThreadsViewSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}