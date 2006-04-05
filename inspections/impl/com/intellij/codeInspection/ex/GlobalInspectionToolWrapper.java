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
import com.intellij.openapi.project.Project;
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
  @NotNull private GlobalInspectionTool myTool;

  public GlobalInspectionToolWrapper(@NotNull GlobalInspectionTool globalInspectionTool) {
    myTool = globalInspectionTool;
  }

  public void initialize(GlobalInspectionContextImpl context) {
    super.initialize(context);
    final RefGraphAnnotator annotator = myTool.getAnnotator(getRefManager());
    if (annotator != null) {
      ((RefManagerImpl)getRefManager()).registerGraphAnnotator(annotator);
    }
  }

  public void runInspection(final AnalysisScope scope, final InspectionManager manager) {
    myTool.runInspection(scope, manager, getContext(), this);
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    return myTool.queryExternalUsagesRequests(manager, getContext(), this);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return isGraphNeeded() ? new JobDescriptor[]{GlobalInspectionContextImpl.BUILD_GRAPH}: JobDescriptor.EMPTY_ARRAY;
  }

  public String getDisplayName() {
    return myTool.getDisplayName();
  }

  public String getGroupDisplayName() {
    return myTool.getGroupDisplayName();
  }

  @NonNls
  public String getShortName() {
    return myTool.getShortName();
  }

  public boolean isEnabledByDefault() {
    return myTool.isEnabledByDefault();
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return myTool.getDefaultLevel();
  }

  public void readSettings(Element element) throws InvalidDataException {
    myTool.readSettings(element);
  }

  public void writeSettings(Element element) throws WriteExternalException {
    myTool.writeSettings(element);
  }

  public JComponent createOptionsPanel() {
    return myTool.createOptionsPanel();
  }

  public boolean isGraphNeeded() {
    return myTool.isGraphNeeded();
  }

  @NotNull public GlobalInspectionTool getTool() {
    return myTool;
  }

  public void processFile(final AnalysisScope analysisScope,
                          final InspectionManager manager,
                          final GlobalInspectionContext context,
                          final boolean filterSuppressed) {
    context.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (filterSuppressed && context.isSuppressed(refEntity, myTool.getShortName())) return;
        CommonProblemDescriptor[] descriptors = myTool.checkElement(refEntity, analysisScope, manager, context);
        if (descriptors != null) {
          addProblemElement(refEntity, descriptors);
        }
      }
    });
  }
  public void projectOpened(Project project) {
    myTool.projectOpened(project);
  }

  public void projectClosed(Project project) {
    myTool.projectClosed(project);
  }
}
