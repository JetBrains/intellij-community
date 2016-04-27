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

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.export.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class ExportHTMLAction extends AnAction implements DumbAware {
  private final InspectionResultsView myView;
  @NonNls private static final String PROBLEMS = "problems";
  @NonNls private static final String HTML = "HTML";
  @NonNls private static final String XML = "XML";

  public ExportHTMLAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.export.html"), null, AllIcons.Actions.Export);
    myView = view;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<String>(InspectionsBundle.message("inspection.action.export.popup.title"), HTML, XML) {
        @Override
        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          exportHTML(Comparing.strEqual(selectedValue, HTML));
          return PopupStep.FINAL_CHOICE;
        }
      });
    InspectionResultsView.showPopup(e, popup);
  }

  private void exportHTML(final boolean exportToHTML) {
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myView.getProject(), exportToHTML);
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myView.getProject());
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "inspections";
    }
    exportToHTMLDialog.reset();
    if (!exportToHTMLDialog.showAndGet()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final Runnable exportRunnable = new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                if (!exportToHTML) {
                  dump2xml(outputDirectoryName);
                }
                else {
                  try {
                    new InspectionTreeHtmlWriter(myView.getTree(), outputDirectoryName);
                  }
                  catch (ProcessCanceledException e) {
                    // Do nothing here.
                  }
                }
              }
            });
          }
        };

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable,
                                                                               InspectionsBundle.message(exportToHTML
                                                                                                         ? "inspection.generating.html.progress.title"
                                                                                                         : "inspection.generating.xml.progress.title"), true,
                                                                               myView.getProject())) {
          return;
        }

        if (exportToHTML && exportToHTMLSettings.OPEN_IN_BROWSER) {
          BrowserUtil.browse(new File(exportToHTMLSettings.OUTPUT_DIRECTORY, "index.html"));
        }
      }
    });
  }

  private void dump2xml(final String outputDirectoryName) {
    try {
      final File outputDir = new File(outputDirectoryName);
      if (!outputDir.exists() && !outputDir.mkdirs()) {
        throw new IOException("Cannot create \'" + outputDir + "\'");
      }
      final InspectionTreeNode root = myView.getTree().getRoot();
      final IOException[] ex = new IOException[1];
      TreeUtil.traverse(root, new TreeUtil.Traverse() {
        @Override
        public boolean accept(final Object node) {
          if (node instanceof InspectionNode) {
            InspectionNode toolNode = (InspectionNode)node;
            Element problems = new Element(PROBLEMS);
            InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();

            final Set<InspectionToolWrapper> toolWrappers = getWorkedTools(toolNode);
            for (InspectionToolWrapper wrapper : toolWrappers) {
              InspectionToolPresentation presentation = myView.getGlobalInspectionContext().getPresentation(wrapper);
              if (!toolNode.isExcluded(myView.getExcludedManager())) {
                final Set<RefEntity> excludedEntities = new HashSet<>();
                final Set<CommonProblemDescriptor> excludedDescriptors = new HashSet<>();
                TreeUtil.traverse(toolNode, o -> {
                  InspectionTreeNode n = (InspectionTreeNode)o;
                  if (n.isExcluded(myView.getExcludedManager())) {
                    if (n instanceof RefElementNode) {
                      excludedEntities.add(((RefElementNode)n).getElement());
                    }
                    if (n instanceof ProblemDescriptionNode) {
                      excludedDescriptors.add(((ProblemDescriptionNode)n).getDescriptor());
                    }
                  }
                  return true;
                });
                presentation.exportResults(problems, excludedEntities, excludedDescriptors);
              }
            }
            PathMacroManager.getInstance(myView.getProject()).collapsePaths(problems);
            try {
              if (problems.getContentSize() != 0) {
                JDOMUtil.writeDocument(new Document(problems),
                                       outputDirectoryName + File.separator + toolWrapper.getShortName() + InspectionApplication.XML_EXTENSION,
                                       CodeStyleSettingsManager.getSettings(null).getLineSeparator());
              }
            }
            catch (IOException e) {
              ex[0] = e;
            }
          }
          return true;
        }
      });
      if (ex[0] != null) {
        throw ex[0];
      }
      final Element element = new Element(InspectionApplication.INSPECTIONS_NODE);
      final String profileName = myView.getCurrentProfileName();
      if (profileName != null) {
        element.setAttribute(InspectionApplication.PROFILE, profileName);
      }
      JDOMUtil.writeDocument(new Document(element),
                             outputDirectoryName + File.separator + InspectionApplication.DESCRIPTIONS + InspectionApplication.XML_EXTENSION,
                             CodeStyleSettingsManager.getSettings(null).getLineSeparator());
    }
    catch (final IOException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(myView, e.getMessage());
        }
      });
    }
  }

  @NotNull
  private Set<InspectionToolWrapper> getWorkedTools(@NotNull InspectionNode node) {
    final Set<InspectionToolWrapper> result = new HashSet<InspectionToolWrapper>();
    final InspectionToolWrapper wrapper = node.getToolWrapper();
    if (myView.getCurrentProfileName() != null){
      result.add(wrapper);
      return result;
    }
    final String shortName = wrapper.getShortName();
    final GlobalInspectionContextImpl context = myView.getGlobalInspectionContext();
    final Tools tools = context.getTools().get(shortName);
    if (tools != null) {   //dummy entry points tool
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        result.add(toolWrapper);
      }
    }
    return result;
  }
}
