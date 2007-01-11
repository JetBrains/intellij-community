/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.HTMLExportFrameMaker;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionGroupNode;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;

import java.io.File;
import java.util.*;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class ExportHTMLAction extends AnAction {
  private InspectionResultsView myView;

  public ExportHTMLAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.export.html"), null, IconLoader.getIcon("/actions/export.png"));
    myView = view;
  }

  public void actionPerformed(AnActionEvent e) {
    exportHTML();
  }

  private void exportHTML() {
  ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myView.getProject());
  final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myView.getProject());
  if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
    exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "exportToHTML";
  }
  exportToHTMLDialog.reset();
  exportToHTMLDialog.show();
  if (!exportToHTMLDialog.isOK()) {
    return;
  }
  exportToHTMLDialog.apply();

  final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
  ApplicationManager.getApplication().invokeLater(new Runnable() {
    public void run() {
      final Runnable exportRunnable = new Runnable() {
        public void run() {
          HTMLExportFrameMaker maker = new HTMLExportFrameMaker(outputDirectoryName, myView.getProject());
          maker.start();
          try {
            exportHTML(maker);
          }
          catch (ProcessCanceledException e) {
            // Do nothing here.
          }

          maker.done();
        }
      };

      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable, InspectionsBundle.message(
        "inspection.generating.html.progress.title"), true, myView.getProject())) {
        return;
      }

      if (exportToHTMLSettings.OPEN_IN_BROWSER) {
        BrowserUtil.launchBrowser(exportToHTMLSettings.OUTPUT_DIRECTORY + File.separator + "index.html");
      }
    }
  });
}

  public void exportHTML(HTMLExportFrameMaker frameMaker) {
      final InspectionTreeNode root = myView.getTree().getRoot();
      final Enumeration children = root.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode node = (InspectionTreeNode)children.nextElement();
        if (node instanceof InspectionNode) {
          exportHTML(frameMaker, (InspectionNode)node);
        }
        else if (node instanceof InspectionGroupNode) {
          final Enumeration groupChildren = node.children();
          while (groupChildren.hasMoreElements()) {
            InspectionNode toolNode = (InspectionNode)groupChildren.nextElement();
            exportHTML(frameMaker, toolNode);
          }
        }
      }
    }

    private void exportHTML(HTMLExportFrameMaker frameMaker, InspectionNode node) {
      InspectionTool tool = node.getTool();
      HTMLExporter exporter = new HTMLExporter(frameMaker.getRootFolder() + "/" + tool.getFolderName(),
                                               tool.getComposer(), myView.getProject());
      frameMaker.startInspection(tool);
      exportHTML(tool, exporter);
      exporter.generateReferencedPages();
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public void exportHTML(InspectionTool tool, HTMLExporter exporter) {
      StringBuffer packageIndex = new StringBuffer();
      packageIndex.append("<html><body>");

      final Map<String, Set<RefElement>> content = tool.getPackageContent();
      ArrayList<String> packageNames = new ArrayList<String>(content.keySet());

      Collections.sort(packageNames, RefEntityAlphabeticalComparator.getInstance());
      for (String packageName : packageNames) {
        appendPackageReference(packageIndex, packageName);
        final ArrayList<RefElement> packageContent = new ArrayList<RefElement>(content.get(packageName));
        Collections.sort(packageContent, RefEntityAlphabeticalComparator.getInstance());
        StringBuffer contentIndex = new StringBuffer();
        contentIndex.append("<html><body>");
        for (RefElement refElement : packageContent) {
          if (refElement instanceof RefImplicitConstructor) {
            //noinspection AssignmentToForLoopParameter
            refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
          }

          contentIndex.append("<a HREF=\"");
          contentIndex.append(exporter.getURL(refElement));
          contentIndex.append("\" target=\"elementFrame\">");
          contentIndex.append(refElement.getName());
          contentIndex.append("</a><br>");

          exporter.createPage(refElement);
        }

        contentIndex.append("</body></html>");
        HTMLExporter.writeFile(exporter.getRootFolder(), packageName + "-index.html", contentIndex, myView.getProject());
      }

      final Set<RefModule> modules = tool.getModuleProblems();
      if (modules != null) {
        final List<RefModule> sortedModules = new ArrayList<RefModule>(modules);
        Collections.sort(sortedModules, RefEntityAlphabeticalComparator.getInstance());
        for (RefModule module : sortedModules) {
          appendPackageReference(packageIndex, module.getName());
          StringBuffer contentIndex = new StringBuffer();
          contentIndex.append("<html><body>");

          contentIndex.append("<a HREF=\"");
          contentIndex.append(exporter.getURL(module));
          contentIndex.append("\" target=\"elementFrame\">");
          contentIndex.append(module.getName());
          contentIndex.append("</a><br>");
          exporter.createPage(module);

          contentIndex.append("</body></html>");
          HTMLExporter.writeFile(exporter.getRootFolder(), module.getName() + "-index.html", contentIndex, myView.getProject());
        }
      }


      packageIndex.append("</body></html>");

      HTMLExporter.writeFile(exporter.getRootFolder(), "index.html", packageIndex, myView.getProject());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private static void appendPackageReference(StringBuffer packageIndex, String packageName) {
      packageIndex.append("<a HREF=\"");
      packageIndex.append(packageName);
      packageIndex.append("-index.html\" target=\"packageFrame\">");
      packageIndex.append(packageName);
      packageIndex.append("</a><br>");
    }

}
