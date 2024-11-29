// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@ApiStatus.Internal
public final class RainbowColorsInSchemeState {
  public static final String DEFAULT_LANGUAGE_NAME = "Default";
  private final EditorColorsScheme myEditedScheme;
  private final EditorColorsScheme myOriginalScheme;

  public RainbowColorsInSchemeState(@NotNull EditorColorsScheme editedScheme,
                                    @NotNull EditorColorsScheme originalScheme) {
    myEditedScheme = editedScheme;
    myOriginalScheme = originalScheme;
  }

  public void apply(@Nullable EditorColorsScheme scheme) {
    if (scheme != null && scheme != myEditedScheme) {
      RainbowHighlighter.transferRainbowState(scheme, myEditedScheme);
      for (TextAttributesKey key : RainbowHighlighter.RAINBOW_COLOR_KEYS) {
        Color color = myEditedScheme.getAttributes(key).getForegroundColor();
        if (!color.equals(scheme.getAttributes(key).getForegroundColor()) ) {
          scheme.setAttributes(key, RainbowHighlighter.createRainbowAttribute(color));
        }
      }
      updateRainbowMarkup(scheme);
    }
  }

  @ApiStatus.Internal
  public static void updateRainbowMarkup(@NotNull EditorColorsScheme scheme) {
    Set<String> languagesWithRainbowHighlighting = getRainbowOnLanguageIds(scheme);
    reportStatistic(languagesWithRainbowHighlighting);

    RainbowHighlighter.resetRainbowGeneratedColors(scheme);
    ApplicationManager
      .getApplication()
      .getMessageBus()
      .syncPublisher(RainbowStateChangeListener.getTOPIC())
      .onRainbowStateChanged(scheme, languagesWithRainbowHighlighting);

    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    for (Editor editor : allEditors) {
      final Project project = editor.getProject();
      if (project != null) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file != null) {
          DaemonCodeAnalyzer.getInstance(project).restart(file);
        }
      }
    }
  }

  private static void reportStatistic(@NotNull Set<String> languagesWithRainbowHighlighting) {
    Set<String> logCopy = new TreeSet<>(languagesWithRainbowHighlighting);
    boolean rainbowOnByDefault = logCopy.remove(DEFAULT_LANGUAGE_NAME);
    RainbowCollector.getRAINBOW_HIGHLIGHTER_CHANGED_EVENT().log(
      rainbowOnByDefault,
      logCopy.stream().toList());
  }

  @NotNull
  private static @UnmodifiableView Set<String> getRainbowOnLanguageIds(@NotNull EditorColorsScheme scheme) {
    TreeSet<String> rainbowOnLanguages = new TreeSet<>();
    ColorSettingsPage.EP_NAME.forEachExtensionSafe(
      it -> {
          if (it instanceof RainbowColorSettingsPage rcp  && RainbowHighlighter.isRainbowEnabledWithInheritance(scheme, rcp.getLanguage())) {
            Language language = rcp.getLanguage();
            if (language != Language.ANY) {
              // Here we skip [Language.ANY] as the language that has no frontend representation
              // Instead, the [null] language is the Default language
              // See the [com.jetbrains.rdclient.colorSchemes.ProtocolRainbowColorSettingsPage.getLanguage] implementation
              rainbowOnLanguages.add(language != null
                                     ? language.getID()
                                     : DEFAULT_LANGUAGE_NAME);
            }
          }
        }
    );
    return Collections.unmodifiableSet(rainbowOnLanguages);
  }

  public boolean isModified(@Nullable Language language) {
    return (language == null && isRainbowColorsModified())
           || RainbowHighlighter.isRainbowEnabled(myEditedScheme, language) != RainbowHighlighter.isRainbowEnabled(myOriginalScheme, language);
  }

  private boolean isRainbowColorsModified() {
    for (TextAttributesKey key : RainbowHighlighter.RAINBOW_COLOR_KEYS) {
      if (!myEditedScheme.getAttributes(key).equals(myOriginalScheme.getAttributes(key)) ) {
        return true;
      }
    }
    return false;
  }
}
