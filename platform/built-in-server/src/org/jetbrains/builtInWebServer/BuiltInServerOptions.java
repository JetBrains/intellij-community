// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerBundle;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.BuiltInServerManagerImpl;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.io.CustomPortServerManagerBase;

@State(
  name = "BuiltInServerOptions",
  category =  SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)
)
public final class BuiltInServerOptions implements PersistentStateComponent<BuiltInServerOptions> {
  public static final int DEFAULT_PORT = 63342;

  @Attribute
  public int builtInServerPort = DEFAULT_PORT;
  @Attribute
  public boolean builtInServerAvailableExternally = false;

  @Attribute
  public boolean allowUnsignedRequests = false;

  public static BuiltInServerOptions getInstance() {
    return ApplicationManager.getApplication().getService(BuiltInServerOptions.class);
  }

  @Override
  public @NotNull BuiltInServerOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull BuiltInServerOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public int getEffectiveBuiltInServerPort() {
    MyCustomPortServerManager portServerManager = CustomPortServerManager.EP_NAME.findExtensionOrFail(MyCustomPortServerManager.class);
    if (!portServerManager.isBound()) {
      return BuiltInServerManager.getInstance().getPort();
    }
    return builtInServerPort;
  }

  public static final class MyCustomPortServerManager extends CustomPortServerManagerBase {
    @Override
    public void cannotBind(@NotNull Exception e, int port) {
      String message = BuiltInServerBundle.message("notification.content.cannot.start.built.in.http.server.on.custom.port", port, ApplicationNamesInfo.getInstance().getFullProductName());
      new Notification(BuiltInServerManagerImpl.NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(null);
    }

    @Override
    public int getPort() {
      int port = getInstance().builtInServerPort;
      return port == DEFAULT_PORT ? -1 : port;
    }

    @Override
    public boolean isAvailableExternally() {
      return getInstance().builtInServerAvailableExternally;
    }
  }

  public static void onBuiltInServerPortChanged() {
    CustomPortServerManager.EP_NAME.forEachExtensionSafe(extension -> {
      if (extension instanceof CustomPortServerManagerBase baseManager) {
        baseManager.portChanged();
      }
    });
  }
}
