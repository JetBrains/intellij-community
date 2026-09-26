// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;


public final class TooltipLinkHandlerEP extends BaseKeyedLazyInstance<TooltipLinkHandler> {
  public static final ExtensionPointName<TooltipLinkHandlerEP> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.linkHandler");

  @Attribute("prefix")
  public String prefix;

  @Attribute("handlerClass")
  public String handlerClassName;

  @Override
  protected @Nullable String getImplementationClassName() {
    return handlerClassName;
  }

  public static boolean handleLink(@NotNull String ref, @NotNull Editor editor) {
    Boolean handled = withHandler(
      ref,
      (handler, refSuffix) -> handler.handleLink(refSuffix.replaceAll("<br/>", "\n"), editor)
    );
    return Boolean.TRUE == handled;
  }

  public static @Nullable @InspectionMessage String getDescription(@NotNull String ref, @NotNull Editor editor) {
    return withHandler(
      ref,
      (handler, refSuffix) -> handler.getDescription(refSuffix, editor)
    );
  }

  public static @NotNull String getDescriptionTitle(@NotNull String ref, @NotNull Editor editor) {
    String title = withHandler(
      ref,
      (handler, refSuffix) -> handler.getDescriptionTitle(refSuffix, editor)
    );
    return title != null ? title : IdeBundle.message("inspection.message.inspection.info");
  }

  private static <T> @Nullable T withHandler(
    @NotNull String ref,
    @NotNull BiFunction<@NotNull TooltipLinkHandler, @NotNull String, @Nullable T> func
  ) {
    return EP_NAME.computeSafeIfAny(ep -> {
      if (ref.startsWith(ep.prefix)) {
        String refSuffix = ref.substring(ep.prefix.length());
        return func.apply(ep.getInstance(), refSuffix);
      }
      return null;
    });
  }
}
