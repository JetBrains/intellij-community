package com.intellij.usages.impl.rules;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(
  name = "JavaUsageViewSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class JavaUsageViewSettings implements PersistentStateComponent<JavaUsageViewSettings> {
  public static JavaUsageViewSettings getInstance() {
    return ServiceManager.getService(JavaUsageViewSettings.class);
  }

  public boolean SHOW_IMPORTS = true;

  public JavaUsageViewSettings getState() {
    return this;
  }

  public void loadState(final JavaUsageViewSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
