/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 09-Jan-2007
 */
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineInspectionRVContentProvider;
import com.intellij.codeInspection.offlineViewer.OfflineViewParseUtil;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ViewOfflineResultsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.actions.ViewOfflineResultsAction");
  @NonNls private static final String XML_EXTENSION = "xml";

  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    presentation.setEnabled(project != null);
    presentation.setVisible(ActionPlaces.isMainMenuOrActionSearch(event.getPlace()) && !PlatformUtils.isCidr());
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);

    LOG.assertTrue(project != null);

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false){
      @Override
      public Icon getIcon(VirtualFile file) {
        if (file.isDirectory()) {
          if (file.findChild(InspectionApplication.DESCRIPTIONS + "." + StdFileTypes.XML.getDefaultExtension()) != null) {
            return AllIcons.Nodes.InspectionResults;
          }
        }
        return super.getIcon(file);
      }
    };
    descriptor.setTitle("Select Path");
    descriptor.setDescription("Select directory which contains exported inspections results");
    final VirtualFile virtualFile = FileChooser.chooseFile(descriptor, project, null);
    if (virtualFile == null || !virtualFile.isDirectory()) return;

    final Map<String, Map<String, Set<OfflineProblemDescriptor>>> resMap =
      new HashMap<String, Map<String, Set<OfflineProblemDescriptor>>>();
    final String [] profileName = new String[1];
    final Runnable process = new Runnable() {
      @Override
      public void run() {
        final VirtualFile[] files = virtualFile.getChildren();
        try {
          for (final VirtualFile inspectionFile : files) {
            if (inspectionFile.isDirectory()) continue;
            final String shortName = inspectionFile.getNameWithoutExtension();
            final String extension = inspectionFile.getExtension();
            if (shortName.equals(InspectionApplication.DESCRIPTIONS)) {
              profileName[0] = ApplicationManager.getApplication().runReadAction(
                  new Computable<String>() {
                    @Override
                    @Nullable
                    public String compute() {
                      return OfflineViewParseUtil.parseProfileName(LoadTextUtil.loadText(inspectionFile).toString());
                    }
                  }
              );
            }
            else if (XML_EXTENSION.equals(extension)) {
              resMap.put(shortName, ApplicationManager.getApplication().runReadAction(
                  new Computable<Map<String, Set<OfflineProblemDescriptor>>>() {
                    @Override
                    public Map<String, Set<OfflineProblemDescriptor>> compute() {
                      return OfflineViewParseUtil.parse(LoadTextUtil.loadText(inspectionFile).toString());
                    }
                  }
              ));
            }
          }
        }
        catch (final Exception e) {  //all parse exceptions
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showInfoMessage(e.getMessage(), InspectionsBundle.message("offline.view.parse.exception.title"));
            }
          });
          throw new ProcessCanceledException(); //cancel process
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(project, InspectionsBundle.message("parsing.inspections.dump.progress.title"), process, new Runnable() {
      @Override
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          @Override
          public void run() {
            final String name = profileName[0];
            showOfflineView(project, name, resMap,
                            InspectionsBundle.message("offline.view.title") +
                            " (" + (name != null ? name : InspectionsBundle.message("offline.view.editor.settings.title")) +")");
          }
        });
      }
    }, null, new PerformAnalysisInBackgroundOption(project));
  }

  @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"}) //used in TeamCity
  public static InspectionResultsView showOfflineView(@NotNull Project project,
                                                      @Nullable
                                                      final String profileName,
                                                      @NotNull final Map<String, Map<String, Set<OfflineProblemDescriptor>>> resMap,
                                                      @NotNull String title) {
    Profile profile;
    if (profileName != null) {
      profile = InspectionProjectProfileManager.getInstance(project).getProfile(profileName, false);
      if (profile == null) {
        profile = InspectionProfileManager.getInstance().getProfile(profileName, false);
      }
    }
    else {
      profile = null;
    }
    final InspectionProfile inspectionProfile;
    if (profile != null) {
      inspectionProfile = (InspectionProfile)profile;
    }
    else {
      inspectionProfile = new InspectionProfileImpl(profileName != null ? profileName : "Server Side") {
        @Override
        public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
          return resMap.containsKey(key.toString());
        }

        @Override
        public HighlightDisplayLevel getErrorLevel(@NotNull final HighlightDisplayKey key, PsiElement element) {
          return ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getErrorLevel(key, element);
        }

        @Override
        public boolean isEditable() {
          return false;
        }

        @NotNull
        @Override
        public String getDisplayName() {
          return getName();
        }
      };
    }
    return showOfflineView(project, resMap, inspectionProfile, title);
  }

  @NotNull
  public static InspectionResultsView showOfflineView(@NotNull Project project,
                                                      @NotNull Map<String, Map<String, Set<OfflineProblemDescriptor>>> resMap,
                                                      @NotNull InspectionProfile inspectionProfile,
                                                      @NotNull String title) {
    final AnalysisScope scope = new AnalysisScope(project);
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);
    context.setExternalProfile(inspectionProfile);
    context.setCurrentScope(scope);
    context.initializeTools(new ArrayList<Tools>(), new ArrayList<Tools>(), new ArrayList<Tools>());
    final InspectionResultsView view = new InspectionResultsView(project, inspectionProfile, scope, context,
                                                                 new OfflineInspectionRVContentProvider(resMap, project));
    ((RefManagerImpl)context.getRefManager()).startOfflineView();
    view.update();
    TreeUtil.selectFirstNode(view.getTree());
    context.addView(view, title);
    return view;
  }
}
