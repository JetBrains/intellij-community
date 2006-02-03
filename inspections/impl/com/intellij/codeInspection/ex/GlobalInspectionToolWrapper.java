/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
    myGlobalInspectionTool.runInspection(scope, getManager(), getManager(), this);
  }



  public boolean queryExternalUsagesRequests() {
    return myGlobalInspectionTool.queryExternalUsagesRequests(getManager(), getManager(), this);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return JobDescriptor.EMPTY_ARRAY;
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

  public void processFile(final AnalysisScope analysisScope,
                          final InspectionManager manager,
                          final GlobalInspectionContext context,
                          final boolean filterSuppressed) {
    ((GlobalInspectionContext)manager).getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (filterSuppressed && ((GlobalInspectionContext)manager).isSuppressed(refEntity, myGlobalInspectionTool.getShortName())) return;
        CommonProblemDescriptor[] descriptors = myGlobalInspectionTool.checkElement(refEntity, analysisScope, manager, (GlobalInspectionContext)manager);
        if (descriptors != null) {
          addProblemElement(refEntity, descriptors);
        }
      }
    });
  }
}
