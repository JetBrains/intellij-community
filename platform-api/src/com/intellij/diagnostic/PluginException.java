package com.intellij.diagnostic;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Jan 8, 2004
 * Time: 3:06:43 PM
 * To change this template use Options | File Templates.
 */
public class PluginException extends RuntimeException {
  private PluginId myPluginId;

  public PluginException(String message, Throwable cause, PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
  }

  public PluginException(Throwable e, PluginId pluginId) {
    super (e.getMessage(), e);
    myPluginId = pluginId;
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  public String getMessage() {
    @NonNls String message = super.getMessage();

    if (message == null) {
      message = "";
    }

    message += " [Plugin: " + myPluginId.toString() + "]";
    return message;
  }
}
