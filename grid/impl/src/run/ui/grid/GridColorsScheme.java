package com.intellij.database.run.ui.grid;

import com.intellij.database.settings.DataGridAppearanceSettings;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class GridColorsScheme extends DelegateColorScheme {
  private final boolean myUseConsoleFonts;
  private @Nullable FontPreferences myFontPreferences;
  private Color myDefaultBackground;

  public GridColorsScheme(boolean useConsoleFonts, @Nullable DataGridAppearanceSettings settings) {
    super(EditorColorsManager.getInstance().getGlobalScheme());
    updateFromSettings(settings);
    myUseConsoleFonts = useConsoleFonts;
  }

  public boolean isExplicitDefaultBackground() {
    return myDefaultBackground != null;
  }

  public @NotNull CellAttributes getAttributes(@NotNull CellAttributesKey key) {
    TextAttributes attributes = getAttributes(key.attributes);
    Color color = attributes.getBackgroundColor();
    Color background = key.isUnderlined ? null : color;
    Color effect = key.isUnderlined ? color : null;
    return new CellAttributes(background, effect, key.isUnderlined);
  }

  @Override
  public @NotNull Color getDefaultBackground() {
    return myDefaultBackground != null ? myDefaultBackground : super.getDefaultBackground();
  }

  public void setDefaultBackground(@Nullable Color color) {
    myDefaultBackground = color;
  }

  public void updateFromScheme(boolean useCustomFont, @NotNull GridColorsScheme scheme) {
    if (useCustomFont) {
      myFontPreferences = new FontPreferencesImpl();
      String family = scheme.getFontPreferences().getFontFamily();
      ((FontPreferencesImpl)myFontPreferences).setEffectiveFontFamilies(List.of(family));
      ((FontPreferencesImpl)myFontPreferences).setFontSize(family, scheme.getEditorFontSize());
      ((FontPreferencesImpl)myFontPreferences).setLineSpacing(scheme.getLineSpacing());
    }
    else {
      myFontPreferences = null;
    }
  }

  public void updateFromSettings(@Nullable DataGridAppearanceSettings settings) {
    if (settings != null && settings.getUseGridCustomFont()) {
      myFontPreferences = new FontPreferencesImpl();
      String family = ObjectUtils.notNull(settings.getGridFontFamily(), getDelegate().getFontPreferences().getFontFamily());
      ((FontPreferencesImpl)myFontPreferences).setEffectiveFontFamilies(List.of(family));
      ((FontPreferencesImpl)myFontPreferences).setFontSize(family, settings.getGridFontSize());
      ((FontPreferencesImpl)myFontPreferences).setLineSpacing(settings.getGridLineSpacing());
    }
    else {
      myFontPreferences = null;
    }
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    if (myFontPreferences != null) {
      if (myFontPreferences instanceof ModifiableFontPreferences) {
        ((ModifiableFontPreferences)myFontPreferences).setLineSpacing(lineSpacing);
      }
    }
    else {
      super.setLineSpacing(lineSpacing);
    }
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    if (myFontPreferences != null) {
      if (myFontPreferences instanceof ModifiableFontPreferences) {
        ((ModifiableFontPreferences)myFontPreferences).setFontSize(getEditorFontName(), fontSize);
      }
    }
    else {
      super.setEditorFontSize(fontSize);
    }
  }

  @Override
  public void setEditorFontSize(float fontSize) {
    if (myFontPreferences != null) {
      if (myFontPreferences instanceof ModifiableFontPreferences) {
        ((ModifiableFontPreferences)myFontPreferences).setFontSize(getEditorFontName(), fontSize);
      }
    }
    else {
      super.setEditorFontSize(fontSize);
    }
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    return myFontPreferences != null ? myFontPreferences :
           myUseConsoleFonts ? super.getConsoleFontPreferences() :
           super.getFontPreferences();
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    if (myFontPreferences != null) {
      myFontPreferences = preferences;
    }
    else {
      super.setFontPreferences(preferences);
    }
  }

  public void setCustomFontPreferences(@NotNull FontPreferences preferences) {
    myFontPreferences = preferences;
  }

  @Override
  public String getEditorFontName() {
    return myFontPreferences != null ? myFontPreferences.getFontFamily() :
           myUseConsoleFonts ? super.getConsoleFontName() :
           super.getEditorFontName();
  }

  @Override
  public int getEditorFontSize() {
    return myFontPreferences != null ? myFontPreferences.getSize(getEditorFontName()) :
           myUseConsoleFonts ? super.getConsoleFontSize() :
           super.getEditorFontSize();
  }

  @Override
  public float getEditorFontSize2D() {
    return myFontPreferences != null ? myFontPreferences.getSize2D(getEditorFontName()) :
           myUseConsoleFonts ? super.getConsoleFontSize2D() :
           super.getEditorFontSize2D();
  }

  @Override
  public float getLineSpacing() {
    return myFontPreferences != null ? myFontPreferences.getLineSpacing() :
           myUseConsoleFonts ? super.getConsoleLineSpacing() :
           super.getLineSpacing();
  }

  @Override
  public @NotNull Font getFont(EditorFontType key) {
    if (myFontPreferences == null) {
      return myUseConsoleFonts ? super.getFont(EditorFontType.getConsoleType(key)) : super.getFont(key);
    }
    return myFontPreferences instanceof DelegatingFontPreferences ?
           EditorFontCache.getInstance().getFont(key) :
           FontFamilyService.getFont(myFontPreferences.getFontFamily(), myFontPreferences.getRegularSubFamily(), myFontPreferences.getBoldSubFamily(),
                                     Font.PLAIN, myFontPreferences.getSize2D(myFontPreferences.getFontFamily()));
  }
}
