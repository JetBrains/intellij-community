// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public final class DocumentationSettings {

  private DocumentationSettings() {
  }

  public static boolean isHighlightingOfQuickDocSignaturesEnabled() {
    return ClientDocumentationSettings.getCurrentInstance().isHighlightingOfQuickDocSignaturesEnabled();
  }

  public static boolean isHighlightingOfCodeBlocksEnabled() {
    return ClientDocumentationSettings.getCurrentInstance().isHighlightingOfCodeBlocksEnabled();
  }

  public static boolean isCodeBackgroundEnabled() {
    return ClientDocumentationSettings.getCurrentInstance().isCodeBackgroundEnabled();
  }

  public static boolean isSemanticHighlightingOfLinksEnabled() {
    return ClientDocumentationSettings.getCurrentInstance().isSemanticHighlightingOfLinksEnabled();
  }

  public static @NotNull InlineCodeHighlightingMode getInlineCodeHighlightingMode() {
    return ClientDocumentationSettings.getCurrentInstance().getInlineCodeHighlightingMode();
  }

  /**
   * Allows reducing saturation of highlighting colors in order to lower distraction from the main code.
   * Here 100 is the normal saturation (bright tones) and 0 is zero saturation (grey tones)
   */
  public static float getHighlightingSaturation(boolean isForRenderedDoc) {
    int result = ApplicationManager.getApplication().isUnitTestMode() ? 100 :
                 isForRenderedDoc ? Registry.intValue("documentation.component.highlighting.saturation.for.rendered.docs") :
                 Registry.intValue("documentation.component.highlighting.saturation.for.hints");
    return MathUtil.clamp(result, 0, 100) * 0.01F;
  }

  /**
   * Swing HTML Editor Kit processes values in percents of 'font-size' css property really weirdly
   * and even in not a cross-platform way.
   * So we have to do some hacks to align fonts.
   *
   * @deprecated Use {@link QuickDocCodeHighlightingHelper} for adding code fragments to documentation
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  //TODO Move the code to QuickDocCodeHighlightingHelper once safe to remove
  public static int getMonospaceFontSizeCorrection(boolean isForRenderedDoc) {
    if (isForRenderedDoc) {
      return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 90 : 96;
    }
    else {
      return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 90 : 100;
    }
  }


  @ApiStatus.Experimental
  public enum InlineCodeHighlightingMode {
    NO_HIGHLIGHTING {
      @Override
      public String toString() {
        return AnalysisBundle.message("documentation.settings.inline.code.highlighting.mode.no.highlighting");
      }
    },

    AS_DEFAULT_CODE {
      @Override
      public String toString() {
        return AnalysisBundle.message("documentation.settings.inline.code.highlighting.mode.as.default.code");
      }
    },

    SEMANTIC_HIGHLIGHTING {
      @Override
      public String toString() {
        return AnalysisBundle.message("documentation.settings.inline.code.highlighting.mode.semantic.highlighting");
      }
    }
  }
}
