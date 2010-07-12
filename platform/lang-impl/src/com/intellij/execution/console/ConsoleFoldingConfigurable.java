package com.intellij.execution.console;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.AddDeleteListPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class ConsoleFoldingConfigurable implements SearchableConfigurable {
  private JPanel myMainComponent;
  private MyAddDeleteListPanel myPositivePanel;
  private MyAddDeleteListPanel myNegativePanel;
  private final ConsoleFoldingSettings mySettings;

  public ConsoleFoldingConfigurable(ConsoleFoldingSettings settings) {
    mySettings = settings;
  }

  public JComponent createComponent() {
    if (myMainComponent == null) {
      myMainComponent = new JPanel(new VerticalFlowLayout());
      myPositivePanel = new MyAddDeleteListPanel("To fold console lines that contain:", "Enter a substring of a console line you'd like to see folded:");
      myNegativePanel = new MyAddDeleteListPanel("Exceptions:", "Enter a substring of a console line you don't want to fold:");
      myMainComponent.add(myPositivePanel);
      myMainComponent.add(myNegativePanel);
    }
    return myMainComponent;
  }

  public boolean isModified() {
    return !Arrays.asList(myNegativePanel.getListItems()).equals(mySettings.getNegativePatterns()) ||
           !Arrays.asList(myPositivePanel.getListItems()).equals(mySettings.getPositivePatterns());

  }

  public void apply() throws ConfigurationException {
    myNegativePanel.applyTo(mySettings.getNegativePatterns());
    myPositivePanel.applyTo(mySettings.getPositivePatterns());
  }

  public void reset() {
    myNegativePanel.resetFrom(mySettings.getNegativePatterns());
    myPositivePanel.resetFrom(mySettings.getPositivePatterns());
  }

  public void disposeUIResources() {
    myMainComponent = null;
    myNegativePanel = null;
    myPositivePanel = null;
  }

  public String getId() {
    return getDisplayName();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return "Console Folding";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  private static class MyAddDeleteListPanel extends AddDeleteListPanel {
    private final String myQuery;

    public MyAddDeleteListPanel(String title, String query) {
      super(title, new ArrayList());
      myQuery = query;
    }

    @Override
    @Nullable
    protected Object findItemToAdd() {
      return Messages.showInputDialog(this, myQuery,
                                      "Folding pattern",
                                      Messages.getQuestionIcon(), "", null);
    }

    void resetFrom(List<String> patterns) {
      myListModel.clear();
      for (String pattern : patterns) {
        myListModel.addElement(pattern);
      }
    }

    void applyTo(List<String> patterns) {
      patterns.clear();
      for (Object o : getListItems()) {
        patterns.add((String)o);
      }
    }
  }
}
