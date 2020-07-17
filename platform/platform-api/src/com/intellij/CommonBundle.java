// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.*;

import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * @author yole
 */
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
      return UtilBundle.message(key, params);
    }
    return INSTANCE.getMessage(key, params);
  }

  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (!INSTANCE.containsKey(key)) {
      return () -> UtilBundle.message(key, params);
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
                                        @Nullable String defaultValue,
                                        Object @NotNull ... params) {
    return AbstractBundle.messageOrDefault(bundle, key, defaultValue, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#message(ResourceBundle, String, Object...)} instead
   */
  @Deprecated
  @NotNull
  public static @Nls String message(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return AbstractBundle.message(bundle, key, params);
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
  public static @Nls String getCancelButtonText() {
    return message("button.cancel");
  }

  public static @Nls String getHelpButtonText() {
    return message("button.help");
  }

  public static @Nls String getErrorTitle() {
    return message("title.error");
  }

  /**
   * @deprecated Use more informative title instead
   */
  @Deprecated
  public static @Nls String getWarningTitle() {
    return message("title.warning");
  }

  public static @Nls String getLoadingTreeNodeText() {
    return message("tree.node.loading");
  }

  public static @Nls String getOkButtonText() {
    return message("button.ok");
  }

  public static @Nls String getYesButtonText() {
    return message("button.yes");
  }

  public static @Nls String getNoButtonText() {
    return message("button.no");
  }

  public static @Nls String getContinueButtonText() {
    return message("button.continue");
  }

  public static @Nls String getYesForAllButtonText() {
    return message("button.yes.for.all");
  }

  public static @Nls String getCloseButtonText() {
    return message("button.close");
  }

  @Deprecated
  public static @Nls String getNoForAllButtonText() {
    return message("button.no.for.all");
  }

  public static @Nls String getApplyButtonText() {
    return message("button.apply");
  }

  public static @Nls String getAddButtonText() {
    return message("button.add.a");
  }

  public static @Nls String settingsTitle() {
    return SystemInfo.isMac ? message("title.settings.mac") : message("title.settings");
  }

  public static @Nls String settingsAction() {
    return SystemInfo.isMac ? message("action.settings.mac") : message("action.settings");
  }

  public static @Nls String settingsActionDescription() {
    return SystemInfo.isMac ? message("action.settings.description.mac") : message("action.settings.description");
  }

  public static @Nls String settingsActionPath() {
    return SystemInfo.isMac ? message("action.settings.path.mac") : message("action.settings.path");
  }
}