package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/*
 * @author: MYakovlev
 */

public class CreateFromTemplatePanel{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.ui.CreateFromTemplatePanel");

  private JPanel myMainPanel;
  private JPanel myAttrPanel;
  private JTextField myFilenameField;
  private String[] myUnsetAttributes;
  private ArrayList<Pair<String, JTextField>> myAttributes = new ArrayList<Pair<String,JTextField>>();

  private int myLastRow = 0;

  private int myHorisontalMargin = -1;
  private int myVerticalMargin = -1;
  private boolean mustEnterName;

  public CreateFromTemplatePanel(final String[] unsetAttributes, final boolean mustEnterName){
    this.mustEnterName = mustEnterName;
    myUnsetAttributes = unsetAttributes;
    Arrays.sort(myUnsetAttributes);
  }

  public boolean hasSomethingToAsk() {
    return mustEnterName || myUnsetAttributes.length != 0;
  }

  public JComponent getComponent() {
    if (myMainPanel == null){
      myMainPanel = new JPanel(new GridBagLayout()){
        public Dimension getPreferredSize(){
          return getMainPanelPreferredSize(super.getPreferredSize());
        }
      };
      myAttrPanel = new JPanel(new GridBagLayout());
      JPanel myScrollPanel = new JPanel(new GridBagLayout());
      updateShown();

      myScrollPanel.setBorder(null);
      myScrollPanel.add(myAttrPanel,  new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2), 0, 0));
      myScrollPanel.add(new JPanel(), new GridBagConstraints(0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
      JScrollPane attrScroll = new JScrollPane(myScrollPanel);
      attrScroll.setViewportBorder(null);

      myMainPanel.add(attrScroll, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    }
    return myMainPanel;
  }

  public void ensureFitToScreen(int horisontalMargin, int verticalMargin){
    myHorisontalMargin = horisontalMargin;
    myVerticalMargin = verticalMargin;
  }

  private Dimension getMainPanelPreferredSize(Dimension superPreferredSize){
    if((myHorisontalMargin > 0) && (myVerticalMargin > 0)){
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension preferredSize = superPreferredSize;
      Dimension maxSize = new Dimension(screenSize.width - myHorisontalMargin, screenSize.height - myVerticalMargin);
      int width = Math.min(preferredSize.width, maxSize.width);
      int height = Math.min(preferredSize.height, maxSize.height);
      if(height < preferredSize.height){
        width = Math.min(width + 50, maxSize.width); // to disable horizontal scroller
      }
      preferredSize = new Dimension(width, height);
      return preferredSize;
    }
    else{
      return superPreferredSize;
    }
  }

  private void updateShown() {
    Insets insets = new Insets(2, 2, 2, 2);
    myAttrPanel.add(Box.createHorizontalStrut(200), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 0));
    if(mustEnterName || Arrays.asList(myUnsetAttributes).contains(FileTemplate.ATTRIBUTE_NAME)){
      final JLabel filenameLabel = new JLabel(IdeBundle.message("label.file.name"));
      myAttrPanel.add(filenameLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
      myFilenameField = new JTextField();
      myAttrPanel.add(myFilenameField, new GridBagConstraints(0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
    }

    for (String attribute : myUnsetAttributes) {
      if (attribute.equals(FileTemplate.ATTRIBUTE_NAME)) { // already asked above
        continue;
      }
      JLabel label = new JLabel(attribute.replace('_', ' ') + ":");
      JTextField field = new JTextField();
      myAttributes.add(new Pair<String, JTextField>(attribute, field));
      myAttrPanel.add(label, new GridBagConstraints(0, myLastRow * 2 + 3, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                    insets, 0, 0));
      myAttrPanel.add(field, new GridBagConstraints(0, myLastRow * 2 + 4, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                    GridBagConstraints.HORIZONTAL, insets, 0, 0));
      myLastRow++;
    }

    myAttrPanel.repaint();
    myAttrPanel.revalidate();
    myMainPanel.revalidate();
  }

  @Nullable
  public String getFileName(){
    if (myFilenameField!=null) {
      String fileName = myFilenameField.getText();
      return fileName == null ? "" : fileName;
    } else {
      return null;
    }
  }

  public Properties getProperties(Properties predefinedProperties){
    Properties result = (Properties) predefinedProperties.clone();
    for (Pair<String, JTextField> pair : myAttributes) {
      result.put(pair.first, pair.second.getText());
    }
    return result;
  }
}

