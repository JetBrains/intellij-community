package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  @NonNls private static Map<HighlightDisplayKey, String> ourHighlightDisplayKeyToDescriptionsMap = new HashMap<HighlightDisplayKey, String>();
  static {
    //ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.DEPRECATED_SYMBOL, "Local_DeprecatedSymbol.html");
    //ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_IMPORT, "Local_UnusedImport.html");
    /*ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_SYMBOL,  "Local_UnusedSymbol.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_THROWS_DECL, "Local_UnusedThrowsDeclaration.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.SILLY_ASSIGNMENT, "Local_SillyAssignment.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, "Local_StaticViaInstance.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, "Local_WrongPackage.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.ILLEGAL_DEPENDENCY, "Local_IllegalDependencies.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.JAVADOC_ERROR, "Local_JavaDoc.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, "Local_UnknownJavaDocTags.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.CUSTOM_HTML_TAG, "Local_CustomHtmlTags.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.CUSTOM_HTML_ATTRIBUTE, "Local_CustomHtmlAttributes.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.REQUIRED_HTML_ATTRIBUTE, "Local_NotRequiredHtmlAttributes.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.EJB_ERROR,  "Local_EJBErrors.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.EJB_WARNING, "Local_EJBWarnings.html");*/
    //ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNCHECKED_WARNING, "Local_UncheckedWarning.html");
  }

  private String myText;
  private String myGroup;
  private String myDescriptorFileName;
  private HighlightDisplayKey myKey;
  private JComponent myAdditionalConfigPanel;
  private Element myConfig;
  private InspectionToolsPanel.LevelChooser myChooser;
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
    if (myKey == null) {
      if (tool instanceof LocalInspectionToolWrapper) {
        myKey = HighlightDisplayKey.register(tool.getShortName(), tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
      } else {
        myKey = HighlightDisplayKey.register(tool.getShortName());
      }
    }
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

  public InspectionToolsPanel.LevelChooser getChooser() {
    if (myChooser == null){
      myChooser = new InspectionToolsPanel.LevelChooser();
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
    if (myDescriptorFileName == null){
      return ourHighlightDisplayKeyToDescriptionsMap.get(myKey);
    }
    return myDescriptorFileName;
  }

  public String getGroup() {
    return myGroup;
  }

}
