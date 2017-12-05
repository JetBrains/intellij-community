// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter;
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
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
          return doFinalStep(() -> exportHTML(Comparing.strEqual(selectedValue, HTML)));
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
    ApplicationManager.getApplication().invokeLater(() -> {
      final Runnable exportRunnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
        if (!exportToHTML) {
          dump2xml(outputDirectoryName);
        }
        else {
          try {
            new InspectionTreeHtmlWriter(myView, outputDirectoryName);
          }
          catch (ProcessCanceledException e) {
            // Do nothing here.
          }
        }
      });

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

      final Set<InspectionToolWrapper> visitedWrappers = new THashSet<>();
      TreeUtil.treeNodeTraverser(root).traverse().processEach(node -> {
        if (node instanceof InspectionNode) {
          InspectionNode toolNode = (InspectionNode)node;
          Element problems = new Element(PROBLEMS);
          InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
          if (!visitedWrappers.add(toolWrapper)) return true;

          final Set<InspectionToolWrapper> toolWrappers = getWorkedTools(toolNode);
          for (InspectionToolWrapper wrapper : toolWrappers) {
            InspectionToolPresentation presentation = myView.getGlobalInspectionContext().getPresentation(wrapper);
            if (!toolNode.isExcluded()) {
              presentation.exportResults(problems, presentation::isExcluded, presentation::isExcluded);
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
      });
      if (ex[0] != null) {
        throw ex[0];
      }
      final Element element = new Element(InspectionApplication.INSPECTIONS_NODE);
      final String profileName = myView.getCurrentProfileName();
      if (profileName != null) {
        element.setAttribute(InspectionApplication.PROFILE, profileName);
      }
      JDOMUtil.write(element,
                     new File(outputDirectoryName, InspectionApplication.DESCRIPTIONS + InspectionApplication.XML_EXTENSION),
                     CodeStyleSettingsManager.getSettings(null).getLineSeparator());
    }
    catch (IOException e) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myView, e.getMessage()));
    }
  }

  @NotNull
  private Set<InspectionToolWrapper> getWorkedTools(@NotNull InspectionNode node) {
    final Set<InspectionToolWrapper> result = new HashSet<>();
    final InspectionToolWrapper wrapper = node.getToolWrapper();
    if (myView.getCurrentProfileName() == null){
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
