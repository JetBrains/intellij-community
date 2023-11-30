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

import java.util.Objects;

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

  public static boolean handleLink(final @NotNull String ref, final @NotNull Editor editor) {
    return EP_NAME.computeSafeIfAny(ep -> {
      if (ref.startsWith(ep.prefix)) {
        String refSuffix = ref.substring(ep.prefix.length());
        return ep.getInstance().handleLink(refSuffix.replaceAll("<br/>", "\n"), editor);
      }
      return null;
    }) == Boolean.TRUE;
  }

  public static @Nullable @InspectionMessage String getDescription(final @NotNull String ref, final @NotNull Editor editor) {
    return EP_NAME.computeSafeIfAny(ep -> {
      if (ref.startsWith(ep.prefix)) {
        String refSuffix = ref.substring(ep.prefix.length());
        return ep.getInstance().getDescription(refSuffix, editor);
      }
      return null;
    });
  }

  public static @NotNull String getDescriptionTitle(@NotNull String ref, @NotNull Editor editor) {
    return Objects.requireNonNull(EP_NAME.computeSafeIfAny(ep -> {
      if (ref.startsWith(ep.prefix)) {
        String refSuffix = ref.substring(ep.prefix.length());
        return ep.getInstance().getDescriptionTitle(refSuffix, editor);
      }
      return IdeBundle.message("inspection.message.inspection.info");
    }));
  }
}
