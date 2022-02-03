// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.ide.IdeDeprecatedMessagesBundle;
import com.intellij.openapi.util.NlsContexts.Button;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.*;

import java.util.ResourceBundle;
import java.util.function.Supplier;

@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public final class CommonBundle extends DynamicBundle {
  private static final String BUNDLE = "messages.CommonBundle";
  private static final CommonBundle INSTANCE = new CommonBundle();

  private CommonBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (!INSTANCE.containsKey(key)) {
      return IdeDeprecatedMessagesBundle.message(key, params);
    }
    return INSTANCE.getMessage(key, params);
  }

  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (!INSTANCE.containsKey(key)) {
      return () -> IdeDeprecatedMessagesBundle.message(key, params);
    }
    return INSTANCE.getLazyMessage(key, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#messageOrDefault(ResourceBundle, String, String, Object...)} instead
   */
  @Deprecated
  @Contract("null, _, _, _ -> param3")
  public static @Nls String messageOrDefault(@Nullable ResourceBundle bundle,
                                        @NotNull String key,
                                        @Nullable @Nls String defaultValue,
                                        Object @NotNull ... params) {
    return AbstractBundle.messageOrDefault(bundle, key, defaultValue, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#message(ResourceBundle, String, Object...)} instead
   */
  @Deprecated
  @NotNull
  public static @Nls String message(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(bundle, key, null, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#messageOrNull(ResourceBundle, String, Object...)}
   */
  @Deprecated
  @Nullable
  public static @Nls String messageOfNull(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return AbstractBundle.messageOrNull(bundle, key, params);
  }

  @NotNull
  public static @Button String getCancelButtonText() {
    return message("button.cancel");
  }

  public static @Button String getHelpButtonText() {
    return message("button.help");
  }

  public static @DialogTitle String getErrorTitle() {
    return message("title.error");
  }

  /**
   * @deprecated Use more informative title instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static @DialogTitle String getWarningTitle() {
    return message("title.warning");
  }

  public static @Nls String getLoadingTreeNodeText() {
    return message("tree.node.loading");
  }

  public static @Button String getOkButtonText() {
    return message("button.ok");
  }

  public static @Button String getYesButtonText() {
    return message("button.yes");
  }

  public static @Button String getNoButtonText() {
    return message("button.no");
  }

  public static @Button String getContinueButtonText() {
    return message("button.continue");
  }

  public static @Button String getYesForAllButtonText() {
    return message("button.yes.for.all");
  }

  public static @Button String getCloseButtonText() {
    return message("button.close");
  }

  @Deprecated
  public static @Button String getNoForAllButtonText() {
    return message("button.no.for.all");
  }

  public static @Button String getApplyButtonText() {
    return message("button.apply");
  }

  public static @Button String getAddButtonText() {
    return message("button.add.a");
  }

  public static @DialogTitle String settingsTitle() {
    return SystemInfoRt.isMac ? message("title.settings.mac") : message("title.settings");
  }

  public static @Nls String settingsAction() {
    return SystemInfoRt.isMac ? message("action.settings.mac") : message("action.settings");
  }

  public static @Nls String settingsActionDescription() {
    return SystemInfoRt.isMac ? message("action.settings.description.mac") : message("action.settings.description");
  }

  public static @Nls String settingsActionPath() {
    return SystemInfoRt.isMac ? message("action.settings.path.mac") : message("action.settings.path");
  }
}
