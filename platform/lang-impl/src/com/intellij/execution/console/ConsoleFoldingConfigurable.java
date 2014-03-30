package com.intellij.execution.console;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AddEditDeleteListPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class ConsoleFoldingConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainComponent;
  private MyAddDeleteListPanel myPositivePanel;
  private MyAddDeleteListPanel myNegativePanel;
  private final ConsoleFoldingSettings mySettings = ConsoleFoldingSettings.getSettings();

  @Override
  public JComponent createComponent() {
    if (myMainComponent == null) {
      myMainComponent = new JPanel(new BorderLayout());
      Splitter splitter = new Splitter(true);
      myMainComponent.add(splitter);
      myPositivePanel =
        new MyAddDeleteListPanel("Fold console lines that contain", "Enter a substring of a console line you'd like to see folded:");
      myNegativePanel = new MyAddDeleteListPanel("Exceptions", "Enter a substring of a console line you don't want to fold:");
      splitter.setFirstComponent(myPositivePanel);
      splitter.setSecondComponent(myNegativePanel);

      myPositivePanel.getEmptyText().setText("Fold nothing");
      myNegativePanel.getEmptyText().setText("No exceptions");
    }
    return myMainComponent;
  }

  public void addRule(@NotNull String rule) {
    myPositivePanel.addRule(rule);
  }

  @Override
  public boolean isModified() {
    return !Arrays.asList(myNegativePanel.getListItems()).equals(mySettings.getNegativePatterns()) ||
           !Arrays.asList(myPositivePanel.getListItems()).equals(mySettings.getPositivePatterns());

  }

  @Override
  public void apply() throws ConfigurationException {
    myNegativePanel.applyTo(mySettings.getNegativePatterns());
    myPositivePanel.applyTo(mySettings.getPositivePatterns());
  }

  @Override
  public void reset() {
    myNegativePanel.resetFrom(mySettings.getNegativePatterns());
    myPositivePanel.resetFrom(mySettings.getPositivePatterns());
  }

  @Override
  public void disposeUIResources() {
    myMainComponent = null;
    myNegativePanel = null;
    myPositivePanel = null;
  }

  @Override
  @NotNull
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Console Folding";
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.console.folding";
  }

  private static class MyAddDeleteListPanel extends AddEditDeleteListPanel<String> {
    private final String myQuery;

    public MyAddDeleteListPanel(String title, String query) {
      super(title, new ArrayList<String>());
      myQuery = query;
    }

    @Override
    @Nullable
    protected String findItemToAdd() {
      return showEditDialog("");
    }

    @Nullable
    private String showEditDialog(final String initialValue) {
      return Messages.showInputDialog(this, myQuery, "Folding Pattern", Messages.getQuestionIcon(), initialValue, new InputValidatorEx() {
        @Override
        public boolean checkInput(String inputString) {
           return !StringUtil.isEmpty(inputString);
        }

        @Override
        public boolean canClose(String inputString) {
          return !StringUtil.isEmpty(inputString);
        }

        @Nullable
        @Override
        public String getErrorText(String inputString) {
          if (!checkInput(inputString)) {
            return "Console folding rule string cannot be empty";
          }
          return null;
        }
      });
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

    public void addRule(String rule) {
      addElement(rule);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      super.setBounds(x, y, width, height);
    }

    @Override
    protected String editSelectedItem(String item) {
      return showEditDialog(item);
    }
  }
}
