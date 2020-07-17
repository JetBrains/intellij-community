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
  public static String messageOrDefault(@Nullable ResourceBundle bundle,
                                        @NotNull String key,
                                        @Nullable String defaultValue,
                                        Object @NotNull ... params) {
    return AbstractBundle.messageOrDefault(bundle, key, defaultValue, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#message(ResourceBundle, String, Object...)} instead
   */
  @Deprecated
  @Nls
  @NotNull
  public static String message(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return AbstractBundle.message(bundle, key, params);
  }

  /**
   * @deprecated use {@link AbstractBundle#messageOrNull(ResourceBundle, String, Object...)}
   */
  @Deprecated
  @Nullable
  public static String messageOfNull(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return AbstractBundle.messageOrNull(bundle, key, params);
  }

  @NotNull
  public static String getCancelButtonText() {
    return message("button.cancel");
  }

  public static String getHelpButtonText() {
    return message("button.help");
  }

  public static String getErrorTitle() {
    return message("title.error");
  }

  /**
   * @deprecated Use more informative title instead
   */
  @Deprecated
  public static String getWarningTitle() {
    return message("title.warning");
  }

  public static String getLoadingTreeNodeText() {
    return message("tree.node.loading");
  }

  public static String getOkButtonText() {
    return message("button.ok");
  }

  public static String getYesButtonText() {
    return message("button.yes");
  }

  public static String getNoButtonText() {
    return message("button.no");
  }

  public static String getContinueButtonText() {
    return message("button.continue");
  }

  public static String getYesForAllButtonText() {
    return message("button.yes.for.all");
  }

  public static String getCloseButtonText() {
    return message("button.close");
  }

  @Deprecated
  public static String getNoForAllButtonText() {
    return message("button.no.for.all");
  }

  public static String getApplyButtonText() {
    return message("button.apply");
  }

  public static String getAddButtonText() {
    return message("button.add.a");
  }

  public static String settingsTitle() {
    return SystemInfo.isMac ? message("title.settings.mac") : message("title.settings");
  }

  public static String settingsAction() {
    return SystemInfo.isMac ? message("action.settings.mac") : message("action.settings");
  }

  public static String settingsActionDescription() {
    return SystemInfo.isMac ? message("action.settings.description.mac") : message("action.settings.description");
  }

  public static String settingsActionPath() {
    return SystemInfo.isMac ? message("action.settings.path.mac") : message("action.settings.path");
  }
}