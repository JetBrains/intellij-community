// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.colors;

import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SchemesPanel extends SimpleSchemesPanel<EditorColorsScheme> implements SkipSelfSearchComponent {
  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public SchemesPanel(@NotNull ColorAndFontOptions options) {
    this(options, DEFAULT_VGAP);
  }

  SchemesPanel(@NotNull ColorAndFontOptions options, int vGap) {
    super(vGap);
    myOptions = options;
  }

  private boolean myListLoaded;

  public boolean areSchemesLoaded() {
    return myListLoaded;
  }

  @Override
  protected ActionLink createActionLink() {
    return new ActionLink(ApplicationBundle.message("link.editor.scheme.change.ide.theme"), (actionEvent) -> {
      Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext((ActionLink)actionEvent.getSource()));
      if (settings != null) {
        settings.select(settings.find("preferences.lookFeel"));
      }
    });
  }

  @Nls
  @Override
  protected String getContextHelpLabelText() {
    return ApplicationBundle.message("editbox.scheme.context.help.label");
  }

  void resetSchemesCombo(final Object source) {
    if (this != source) {
      setListLoaded(false);
      EditorColorsScheme selectedSchemeBackup = myOptions.getSelectedScheme();
      resetSchemes(myOptions.getOrderedSchemes());
      selectScheme(selectedSchemeBackup);
      setListLoaded(true);
      myDispatcher.getMulticaster().schemeChanged(this);
    }
  }
  

  private void setListLoaded(final boolean b) {
    myListLoaded = b;
  }

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  protected @NotNull AbstractSchemeActions<EditorColorsScheme> createSchemeActions() {
    return new ColorSchemeActions(this) {
        @Override
        protected @NotNull ColorAndFontOptions getOptions() {
          return myOptions;
        }

        @Override
        protected void onSchemeChanged(@Nullable EditorColorsScheme scheme) {
          if (scheme != null) {
            myOptions.selectScheme(scheme.getName());
            if (areSchemesLoaded()) {
              myDispatcher.getMulticaster().schemeChanged(SchemesPanel.this);
            }
          }
        }

        @Override
        protected void renameScheme(@NotNull EditorColorsScheme scheme, @NotNull String newName) {
          if (myOptions.saveSchemeAs(scheme, newName)) {
            myOptions.removeScheme(scheme);
            myOptions.selectScheme(newName);
          }
        }
      };
  }

  @Override
  public @NotNull SchemesModel<EditorColorsScheme> getModel() {
    return myOptions;
  }

  @Override
  protected boolean supportsProjectSchemes() {
    return false;
  }

  @Override
  protected boolean highlightNonDefaultSchemes() {
    return true;
  }

  @Override
  public boolean useBoldForNonRemovableSchemes() {
    return true;
  }
}
