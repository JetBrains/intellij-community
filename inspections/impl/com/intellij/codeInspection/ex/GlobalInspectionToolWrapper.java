/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public class GlobalInspectionToolWrapper extends DescriptorProviderInspection {
  private GlobalInspectionTool myGlobalInspectionTool;

  public GlobalInspectionToolWrapper(GlobalInspectionTool globalInspectionTool) {
    myGlobalInspectionTool = globalInspectionTool;
  }


  public void initialize(InspectionManagerEx manager) {
    super.initialize(manager);
    final RefGraphAnnotator annotator = myGlobalInspectionTool.getAnnotator(getRefManager());
    if (annotator != null) {
      ((RefManagerImpl)getRefManager()).registerGraphAnnotator(annotator);
    }
  }

  public void runInspection(final AnalysisScope scope) {
    myGlobalInspectionTool.runInspection(scope, getManager(), this);
  }

  public boolean queryExternalUsagesRequests() {
    return myGlobalInspectionTool.queryExternalUsagesRequests(getManager(), getManager(), this);
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[0];
  }

  public String getDisplayName() {
    return myGlobalInspectionTool.getDisplayName();
  }

  public String getGroupDisplayName() {
    return myGlobalInspectionTool.getGroupDisplayName();
  }

  @NonNls
  public String getShortName() {
    return myGlobalInspectionTool.getShortName();
  }

  public boolean isEnabledByDefault() {
    return myGlobalInspectionTool.isEnabledByDefault();
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return myGlobalInspectionTool.getDefaultLevel();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myGlobalInspectionTool.readSettings(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myGlobalInspectionTool.writeSettings(element);
  }

  protected JComponent createOptionsPanel() {
    JComponent provided = myGlobalInspectionTool.createOptionsPanel();
    return provided == null ? super.createOptionsPanel() : provided;
  }

  public boolean isGraphNeeded() {
    return myGlobalInspectionTool.isGraphNeeded();
  }

  public GlobalInspectionTool getTool() {
    return myGlobalInspectionTool;
  }
}
