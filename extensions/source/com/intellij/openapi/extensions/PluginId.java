package com.intellij.openapi.extensions;

import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class PluginId {
  private final static Map<String, PluginId> ourRegisteredIds = new HashMap<String, PluginId>();

  private final String myIdString;

  private PluginId(String idString) {
    myIdString = idString;
  }

  public static PluginId getId(String idString) {
    PluginId pluginId = ourRegisteredIds.get(idString);
    if (pluginId == null) {
      pluginId = new PluginId(idString);
      ourRegisteredIds.put(idString, pluginId);
    }
    return pluginId;
  }

  public String getIdString() {
    return myIdString;
  }

  @Override
  public String toString() {
    return getIdString();
  }
}
