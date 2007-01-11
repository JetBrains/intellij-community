/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 09-Jan-2007
 */
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.offlineViewer.OfflineInspectionResultsViewProvider;
import com.intellij.codeInspection.offlineViewer.OfflineProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineViewParseUtil;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.ui.tree.TreeUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewOfflineResultsAction extends AnAction {

  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(DataKeys.PROJECT);
    presentation.setEnabled(project != null);
    presentation.setVisible(ActionPlaces.MAIN_MENU.equals(event.getPlace()));
  }

  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getData(DataKeys.PROJECT);

    final VirtualFile[] virtualFiles = FileChooser.chooseFiles(project, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    if (virtualFiles == null || virtualFiles.length == 0) return;
    if (!virtualFiles[0].isDirectory()) return;

    final Map<String , Map<String, List<OfflineProblemDescriptor>>> resMap = new HashMap<String, Map<String, List<OfflineProblemDescriptor>>>();
    final VirtualFile[] files = virtualFiles[0].getChildren();
    for (VirtualFile inspectionFile : files) {
      resMap.put(inspectionFile.getNameWithoutExtension(), OfflineViewParseUtil.parse(LoadTextUtil.loadText(inspectionFile).toString()));
    }

    final AnalysisScope scope = new AnalysisScope(project);
    final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(project));
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    InspectionProfile inspectionProfile = null;
    String profileName = null;
    if (profileName != null) {
      final Profile profile = InspectionProjectProfileManager.getInstance(project).getProfile(profileName);
      if (profile != null) {
        inspectionProfile = (InspectionProfile)profile;
      }
      else {
        inspectionProfile = new InspectionProfileImpl("Server Side Profile") {
          public boolean isToolEnabled(final HighlightDisplayKey key) {
            return resMap.containsKey(key.toString());
          }

          public HighlightDisplayLevel getErrorLevel(final HighlightDisplayKey key) {
            return ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getErrorLevel(key);
          }
        };
      }
      inspectionContext.setExternalProfile(inspectionProfile);
    }
    else {
      inspectionContext.RUN_WITH_EDITOR_PROFILE = true;
    }
    inspectionContext.setCurrentScope(scope);
    inspectionContext.initializeTools(scope, new HashMap<String, Set<InspectionTool>>(), new HashMap<String, Set<InspectionTool>>());
    final InspectionResultsView view = new InspectionResultsView(project, inspectionProfile, scope,
                                                                 inspectionContext,
                                                                 new OfflineInspectionResultsViewProvider(resMap));
    ((RefManagerImpl)inspectionContext.getRefManager()).inspectionReadActionStarted();
    view.buildTreeAndSort();
    TreeUtil.selectFirstNode(view.getTree());
    inspectionContext.addView(view, "Offline View" + (profileName != null ? " of " + profileName : ""));
  }
}