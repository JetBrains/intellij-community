// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public class DocumentationSettings {

  private DocumentationSettings() {
  }

  public static boolean isHighlightingOfQuickDocSignaturesEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || AdvancedSettings.getBoolean("documentation.components.enable.doc.highlighting.of.quick.doc.signatures");
  }

  public static boolean isHighlightingOfCodeBlocksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || AdvancedSettings.getBoolean("documentation.components.enable.doc.highlighting.of.code.blocks");
  }

  public static boolean isSemanticHighlightingOfLinksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || AdvancedSettings.getBoolean("documentation.components.enable.doc.semantic.highlighting.of.links");
  }

  public static @NotNull InlineCodeHighlightingMode getInlineCodeHighlightingMode() {
    return ApplicationManager.getApplication().isUnitTestMode()
           ? InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING
           : AdvancedSettings.getEnum("documentation.components.doc.inline.code.highlighting.mode", InlineCodeHighlightingMode.class);
  }

  public static float getHighlightingSaturation() {
    return ApplicationManager.getApplication().isUnitTestMode()
           ? 1.0f
           : MathUtil.clamp(AdvancedSettings.getInt("documentation.components.doc.highlighting.saturation"), 0, 100) * 0.01F;
  }

  /**
   * Swing HTML Editor Kit processes values in percents of 'font-size' css property really weirdly
   * and even in not a cross-platform way.
   * So we have to do some hacks to align fonts.
   */
  public static int getMonospaceFontSizeCorrection() {
    return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 90 : 96;
  }


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
