package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsScheme;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class NewColorAndFontPanel extends JPanel {
  private SchemesPanel mySchemesPanel;
  private OptionsPanel myOptionsPanel;
  private PreviewPanel myPreviewPanel;
  private final String myCategory;

  public NewColorAndFontPanel(final SchemesPanel schemesPanel,
                              final OptionsPanel optionsPanel, final PreviewPanel previewPanel, final String category) {
    super(new BorderLayout());
    mySchemesPanel = schemesPanel;
    myOptionsPanel = optionsPanel;
    myPreviewPanel = previewPanel;
    myCategory = category;

    JPanel top = new JPanel(new BorderLayout());

    top.add(mySchemesPanel, BorderLayout.NORTH);
    top.add(myOptionsPanel.getPanel(), BorderLayout.CENTER);

    if (myPreviewPanel.getPanel() != null) {
      add(top, BorderLayout.NORTH);
      add(myPreviewPanel.getPanel(), BorderLayout.CENTER);
    }
    else {
      add(top, BorderLayout.CENTER);
    }

    previewPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
      @Override
      public void selectionInPreviewChanged(final String typeToSelect) {
        optionsPanel.selectOption(typeToSelect);
      }
    });

    optionsPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
      @Override
      public void settingsChanged() {
        if (schemesPanel.updateDescription(true)) {
          optionsPanel.applyChangesToScheme();
          previewPanel.updateView();
        }
      }
    });


    mySchemesPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
      public void schemeChanged(final EditorColorsScheme scheme) {
        myOptionsPanel.updateOptionsList();
        myPreviewPanel.updateView();
      }
    });

  }

  public static NewColorAndFontPanel create(final PreviewPanel previewPanel, String category, final ColorAndFontOptions options) {
    final SchemesPanel schemesPanel = new SchemesPanel(options);

    final ColorAndFontDescriptionPanel descriptionPanel = new ColorAndFontDescriptionPanel();
    final OptionsPanel optionsPanel = new OptionsPanelImpl(descriptionPanel, options, schemesPanel, category);



    
    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, category);
  }

  public void addOptionListListener(ColorAndFontSettingsListener listener){
    myOptionsPanel.addListener(listener);
  }

  public Runnable showOption(final ColorAndFontOptions colorAndFontOptions, final String option, final boolean highlight) {
    return new Runnable(){
      public void run() {

      }
    };
  }

  public boolean areSchemesLoaded() {
    return mySchemesPanel.areSchemesLoaded();
  }

  public Map<String,String> processListOptions() {
    return null;
  }


  public String getDisplayName() {
    return myCategory;
  }

  public void reset() {
    resetSchemesCombo();
  }

  public void dispose() {
  }

  public void addListener(final ColorAndFontSettingsListener schemeListener) {
    mySchemesPanel.addListener(schemeListener);
  }

  public void resetSchemesCombo() {
    mySchemesPanel.resetSchemesCombo();
  }
}
