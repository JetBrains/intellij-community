// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.colors;

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SchemesPanel extends SimpleSchemesPanel<EditorColorsScheme> {
  private static final Logger LOG = Logger.getInstance(SchemesPanel.class);
  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public SchemesPanel(@NotNull ColorAndFontOptions options) {
    this(options, DEFAULT_VGAP);
  }

  @ApiStatus.Internal
  public SchemesPanel(@NotNull ColorAndFontOptions options, int vGap) {
    super(vGap);
    myOptions = options;
    setEnabled(options.isSchemesPanelEnabled());

    putClientProperty(SearchUtil.SEARCH_SKIP_COMPONENT_KEY, true);
  }

  private boolean myListLoaded;

  public boolean areSchemesLoaded() {
    return myListLoaded;
  }

  @Override
  protected ActionLink createActionLink() {
    String text;

    if (isEnabled()) {
      text = ApplicationBundle.message("link.editor.scheme.change.ide.theme");
    }
    else {
      text = ApplicationBundle.message("link.editor.scheme.configure");
    }

    return new ActionLink(text, (actionEvent) -> {
      Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext((ActionLink)actionEvent.getSource()));
      if (settings != null) {
        settings.select(settings.find("preferences.lookFeel"));
      }
    });
  }

  @Override
  protected @Nullable JLabel createActionLinkCommentLabel() {
    if (isEnabled()) {
      return null;
    }
    else {
      JBLabel label = new JBLabel(ApplicationBundle.message("link.editor.scheme.configure.description"));
      label.setEnabled(false);
      return label;
    }
  }

  @Override
  protected @Nls String getContextHelpLabelText() {
    return ApplicationBundle.message("editbox.scheme.context.help.label");
  }

  void resetSchemesCombo(final Object source) {
    if (this != source) {
      setListLoaded(false);
      EditorColorsScheme selectedSchemeBackup = myOptions.getSelectedScheme();
      resetGroupedSchemes(myOptions.getOrderedSchemes());
      selectScheme(selectedSchemeBackup);
      setListLoaded(true);
      myDispatcher.getMulticaster().schemeChanged(this);
    }
  }
  

  private void setListLoaded(final boolean b) {
    myListLoaded = b;
  }

  protected boolean shouldApplyImmediately() {
    return false;
  }

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  protected @NotNull AbstractSchemeActions<EditorColorsScheme> createSchemeActions() {
    SchemesPanel panel = this;
    return new ColorSchemeActions(panel) {
      @Override
      protected @NotNull ColorAndFontOptions getOptions() {
        return myOptions;
      }

      @Override
      protected void onSchemeChanged(@Nullable EditorColorsScheme scheme) {
        onSchemeChangedFromAction(scheme);
      }

      @Override
      protected void renameScheme(@NotNull EditorColorsScheme scheme, @NotNull String newName) {
        renameSchemeFromAction(scheme, newName);
      }
    };
  }

  protected void onSchemeChangedFromAction(@Nullable EditorColorsScheme scheme) {
    if (scheme != null && areSchemesLoaded()) {
      myOptions.selectScheme(scheme.getName());
      myDispatcher.getMulticaster().schemeChanged(this);

      if (shouldApplyImmediately() && myOptions.isModified()) {
        try {
          myOptions.apply();
        }
        catch (ConfigurationException e) {
          LOG.warn("Unable to apply compiler resource patterns", e);
        }
      }
    }
  }

  protected void renameSchemeFromAction(@NotNull EditorColorsScheme scheme, @NotNull String newName) {
    if (myOptions.saveSchemeAs(scheme, newName)) {
      myOptions.removeScheme(scheme);
      myOptions.selectScheme(newName);
    }
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
    return false;
  }
}
