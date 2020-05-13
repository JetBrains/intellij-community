// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerBundle;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.BuiltInServerManagerImpl;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.io.CustomPortServerManagerBase;

import java.util.Collection;
import java.util.Collections;

@State(
  name = "BuiltInServerOptions",
  storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE)
)
public class BuiltInServerOptions implements PersistentStateComponent<BuiltInServerOptions>, Getter<BuiltInServerOptions> {
  public static final int DEFAULT_PORT = 63342;

  @Attribute
  public int builtInServerPort = DEFAULT_PORT;
  @Attribute
  public boolean builtInServerAvailableExternally = false;

  @Attribute
  public boolean allowUnsignedRequests = false;

  public static BuiltInServerOptions getInstance() {
    return ServiceManager.getService(BuiltInServerOptions.class);
  }

  @Override
  public BuiltInServerOptions get() {
    return this;
  }

  static final class BuiltInServerDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
    @NotNull
    @Override
    public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
      if (category == DebuggerSettingsCategory.GENERAL) {
        return Collections.singletonList(SimpleConfigurable.create("builtInServer", XmlBundle
          .message("setting.builtin.server.category.label"), BuiltInServerConfigurableUi.class, getInstance()));
      }
      return Collections.emptyList();
    }
  }

  @Nullable
  @Override
  public BuiltInServerOptions getState() {
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
      BuiltInServerManagerImpl.NOTIFICATION_GROUP.getValue().createNotification(BuiltInServerBundle.message(
        "notification.content.cannot.start.built.in.http.server.on.custom.port", port,
        ApplicationNamesInfo.getInstance().getFullProductName()), NotificationType.ERROR).notify(null);
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
    CustomPortServerManager.EP_NAME.findExtensionOrFail(MyCustomPortServerManager.class).portChanged();
  }
}