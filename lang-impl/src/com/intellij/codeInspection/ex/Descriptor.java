package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  private final String myText;
  private final String myGroup;
  private final HighlightDisplayKey myKey;
  private JComponent myAdditionalConfigPanel;
  private final Element myConfig;
  private InspectionProfileEntry myTool;
  private final HighlightDisplayLevel myLevel;
  private boolean myEnabled = false;
  private NamedScope myScope;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.Descriptor");

  public Descriptor(Pair< NamedScope, InspectionTool> pair, InspectionProfile inspectionProfile) {
    final InspectionProfileEntry tool = pair.second;
    @NonNls Element config = new Element("options");
    try {
      tool.writeSettings(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    myConfig = config;
    myText = tool.getDisplayName();
    myGroup = tool.getGroupDisplayName().length() == 0 ? InspectionProfileEntry.GENERAL_GROUP_NAME : tool.getGroupDisplayName();
    myKey = HighlightDisplayKey.find(tool.getShortName());
    myLevel = ((InspectionProfileImpl)inspectionProfile).getErrorLevel(myKey, pair.first);
    myEnabled = ((InspectionProfileImpl)inspectionProfile).isToolEnabled(myKey, pair.first);
    myTool = tool;
    myScope = pair.first;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Descriptor)) return false;
    final Descriptor descriptor = (Descriptor)obj;
    return myKey.equals(descriptor.getKey()) &&
           myLevel.equals(descriptor.getLevel()) &&
           myEnabled == descriptor.isEnabled() &&
           Comparing.equal(myScope, descriptor.myScope);
  }

  public int hashCode() {
    final int hash = myKey.hashCode() + 29 * myLevel.hashCode();
    return myScope != null ? myScope.hashCode() + 29 * hash : hash;
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

  @Nullable
  public JComponent getAdditionalConfigPanel() {
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

  public Element getConfig() {
    return myConfig;
  }

  public InspectionProfileEntry getTool() {
    return myTool;
  }

  public String loadDescription() {
    if (!(myTool instanceof InspectionTool)) return null;
    return myTool.loadDescription();
  }

  public String getGroup() {
    return myGroup;
  }

  public NamedScope getScope() {
    return myScope;
  }
}
