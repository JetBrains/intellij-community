package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.io.File;

/**
 * User: anna
 * Date: Dec 7, 2004
 */
public interface InspectionProfile {

  String getName();

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

  InspectionTool getInspectionTool(String shortName);

  InspectionTool[] getInspectionTools();

  LocalInspectionTool[] getHighlightingLocalInspectionTools();

  File getFile();

  InspectionProfileManager getManager();

  void cleanup();

  boolean wasInitialized();

  ModifiableModel getModifiableModel();

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isExecutable();

  interface ModifiableModel {

    InspectionProfile getParentProfile();

    String getBaseProfileName();

    void setBaseProfile(InspectionProfileImpl profile);

    String getName();

    void setName(String name);

    void enableTool(String inspectionTool);

    void disableTool(String inspectionTool);

    void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level);

    HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

    boolean isToolEnabled(HighlightDisplayKey key);

    void commit();

    boolean isChanged();

    void setModified(final boolean toolsSettingsChanged);

    VisibleTreeState getExpandedNodes();

    boolean isProperSetting(HighlightDisplayKey key);

    void setAdditionalJavadocTags(String tags);
    
    void setAdditionalHtmlTags(String tags);
    
    void setAdditionalHtmlAttributes(String attributes);
    
    void setAdditionalNotRequiredHtmlAttributes(String attributes);

    void resetToBase();

    InspectionTool[] getInspectionTools();

    String getAdditionalJavadocTags();
    
    String getAdditionalHtmlTags();
    
    String getAdditionalHtmlAttributes();
    
    String getAdditionalNotRequiredHtmlAttributes();

    void copyFrom(InspectionProfileImpl profile);

    void inheritFrom(InspectionProfileImpl profile);

    UnusedSymbolSettings getUnusedSymbolSettings();

    void setUnusedSymbolSettings(UnusedSymbolSettings settings);

    boolean isDefault();
  }

  static class UnusedSymbolSettings implements JDOMExternalizable{
    public boolean LOCAL_VARIABLE = true;
    public boolean FIELD = true;
    public boolean METHOD = true;
    public boolean CLASS = true;
    public boolean PARAMETER = true;

    public UnusedSymbolSettings copySettings(){
      UnusedSymbolSettings settings = new UnusedSymbolSettings();
      settings.LOCAL_VARIABLE = LOCAL_VARIABLE;
      settings.FIELD = FIELD;
      settings.METHOD = METHOD;
      settings.CLASS = CLASS;
      settings.PARAMETER = PARAMETER;
      return settings;
    }

    public void readExternal(final Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

    public boolean equals(Object object) {
      if (!(object instanceof UnusedSymbolSettings)){
        return false;
      }
      UnusedSymbolSettings that = (UnusedSymbolSettings)object;
      return that.LOCAL_VARIABLE == LOCAL_VARIABLE &&
             that.FIELD == FIELD &&
             that.METHOD == METHOD &&
             that.CLASS == CLASS &&
             that.PARAMETER == PARAMETER;
    }

    public int hashCode() {
      return super.hashCode();
    }
  }
}
