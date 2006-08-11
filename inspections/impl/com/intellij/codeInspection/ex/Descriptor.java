package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  private String myText;
  private String myGroup;
  private String myDescriptorFileName;
  private HighlightDisplayKey myKey;
  private JComponent myAdditionalConfigPanel;
  private Element myConfig;
  private SingleInspectionProfilePanel.LevelChooser myChooser;
  private InspectionProfileEntry myTool;
  private HighlightDisplayLevel myLevel;
  private boolean myEnabled = false;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.Descriptor");


  public Descriptor(HighlightDisplayKey key,
                    ModifiableModel inspectionProfile) {
    myText = HighlightDisplayKey.getDisplayNameByKey(key);
    myGroup = GroupNames.GENERAL_GROUP_NAME;
    myKey = key;
    myConfig = null;
    myEnabled = inspectionProfile.isToolEnabled(key);
    myLevel = inspectionProfile.getErrorLevel(key);
  }

  public Descriptor(InspectionProfileEntry tool, InspectionProfile inspectionProfile) {
    @NonNls Element config = new Element("options");
    try {
      tool.writeSettings(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    myConfig = config;
    myText = tool.getDisplayName();
    myGroup = tool.getGroupDisplayName() != null && tool.getGroupDisplayName().length() == 0 ? GroupNames.GENERAL_GROUP_NAME : tool.getGroupDisplayName();
    myDescriptorFileName = ((InspectionTool)tool).getDescriptionFileName();
    myKey = HighlightDisplayKey.find(tool.getShortName());
    myLevel = inspectionProfile.getErrorLevel(myKey);
    myEnabled = inspectionProfile.isToolEnabled(myKey);
    myTool = tool;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Descriptor)) return false;
    final Descriptor descriptor = (Descriptor)obj;
    return myKey.equals(descriptor.getKey()) &&
           myLevel.equals(descriptor.getLevel()) &&
           myEnabled == descriptor.isEnabled();
  }

  public int hashCode() {
    return myKey.hashCode() + 29 * myLevel.hashCode();
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getText() {
    return myText;
  }

  public HighlightDisplayKey getKey() {
    return myKey;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public JComponent getAdditionalConfigPanel(ModifiableModel inspectionProfile) {
    if (myAdditionalConfigPanel == null && myTool != null){
      myAdditionalConfigPanel = myTool.createOptionsPanel();
      if (myAdditionalConfigPanel == null){
        myAdditionalConfigPanel = new JPanel();
      }
      return myAdditionalConfigPanel;
    }
    return myAdditionalConfigPanel;
  }

  public void resetConfigPanel(){
    myAdditionalConfigPanel = null;
  }

  public SingleInspectionProfilePanel.LevelChooser getChooser() {
    if (myChooser == null){
      myChooser = new SingleInspectionProfilePanel.LevelChooser();
      myChooser.setLevel(myLevel);
    }
    return myChooser;
  }

  public Element getConfig() {
    return myConfig;
  }

  public InspectionProfileEntry getTool() {
    return myTool;
  }

  public String getDescriptorFileName() {
    return myDescriptorFileName;
  }

  public String getGroup() {
    return myGroup;
  }

}
