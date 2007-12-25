/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.HTMLExportFrameMaker;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionGroupNode;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class ExportHTMLAction extends AnAction {
  private InspectionResultsView myView;
  @NonNls private static final String PROBLEMS = "problems";
  @NonNls private static final String HTML = "HTML";
  @NonNls private static final String XML = "XML";

  public ExportHTMLAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.export.html"), null, IconLoader.getIcon("/actions/export.png"));
    myView = view;
  }

  public void actionPerformed(AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<String>(InspectionsBundle.message("inspection.action.export.popup.title"), new String[]{HTML, XML}) {
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
            if (!exportToHTML) {
              dupm2XML(outputDirectoryName);
            } else {
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
          }
        };

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable,
            exportToHTML ? InspectionsBundle.message("inspection.generating.html.progress.title")
            : InspectionsBundle.message("inspection.generating.xml.progress.title"), true, myView.getProject())) {
          return;
        }

        if (exportToHTML && exportToHTMLSettings.OPEN_IN_BROWSER) {
          BrowserUtil.launchBrowser(exportToHTMLSettings.OUTPUT_DIRECTORY + File.separator + "index.html");
        }
      }
    });
  }

  private void dupm2XML(final String outputDirectoryName) {
    try {
      new File(outputDirectoryName).mkdirs();
      final InspectionTreeNode root = myView.getTree().getRoot();
      final IOException[] ex = new IOException[1];
      TreeUtil.traverse(root, new TreeUtil.Traverse() {
        public boolean accept(final Object node) {
          if (node instanceof InspectionNode) {
            InspectionNode toolNode = (InspectionNode)node;
            Element problems = new Element(PROBLEMS);
            final InspectionTool tool = toolNode.getTool();
            final Set<InspectionTool> tools = getWorkedTools(toolNode);
            for (InspectionTool inspectionTool : tools) {
              inspectionTool.exportResults(problems);
            }
            PathMacroManager.getInstance(myView.getProject()).collapsePaths(problems);
            try {
              JDOMUtil.writeDocument(new Document(problems),
                                     outputDirectoryName + File.separator + tool.getShortName() + XmlFileType.DOT_DEFAULT_EXTENSION,
                                     CodeStyleSettingsManager.getSettings(null).getLineSeparator());
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
                             outputDirectoryName + File.separator + InspectionApplication.DESCRIPTIONS + XmlFileType.DOT_DEFAULT_EXTENSION,
                             CodeStyleSettingsManager.getSettings(null).getLineSeparator());
    }
    catch (final IOException e) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(myView, e.getMessage());
        }
      });
    }
  }

  private Set<InspectionTool> getWorkedTools(InspectionNode node) {
    final Set<InspectionTool> result = new HashSet<InspectionTool>();
    final InspectionTool tool = node.getTool();
    if (myView.getCurrentProfileName() != null){
      result.add(tool);
      return result;
    }
    final String shortName = tool.getShortName();
    final GlobalInspectionContextImpl context = myView.getGlobalInspectionContext();
    final Set<Pair<InspectionTool,InspectionProfile>> tools = context.getTools().get(shortName);
    if (tools != null) {   //dummy entry points tool
      for (Pair<InspectionTool, InspectionProfile> pair : tools) {
        result.add(pair.first);
      }
    }
    return result;
  }

  private void exportHTML(HTMLExportFrameMaker frameMaker) {
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
    Set<InspectionTool> tools = getWorkedTools(node);
    final InspectionTool tool = node.getTool();
    HTMLExporter exporter =
      new HTMLExporter(frameMaker.getRootFolder() + "/" + tool.getShortName(), tool.getComposer(), myView.getProject());
    frameMaker.startInspection(tool);
    exportHTML(tools, exporter);
    exporter.generateReferencedPages();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void exportHTML(Set<InspectionTool> tools, HTMLExporter exporter) {
    StringBuffer packageIndex = new StringBuffer();
    packageIndex.append("<html><body>");

    final Map<String, Set<RefEntity>> content = new HashMap<String, Set<RefEntity>>();

    for (InspectionTool tool : tools) {
      content.putAll(tool.getContent());
    }

    final Set<RefEntity> defaultPackageEntities = content.remove(null);
    if (defaultPackageEntities != null) {
      content.put("default package" , defaultPackageEntities);
    }

    ArrayList<String> packageNames = new ArrayList<String>(content.keySet());

    Collections.sort(packageNames);
    for (String packageName : packageNames) {
      appendPackageReference(packageIndex, packageName);
      final ArrayList<RefEntity> packageContent = new ArrayList<RefEntity>(content.get(packageName));
      Collections.sort(packageContent, RefEntityAlphabeticalComparator.getInstance());
      StringBuffer contentIndex = new StringBuffer();
      contentIndex.append("<html><body>");
      for (RefEntity refElement : packageContent) {
        refElement = refElement.getRefManager().getRefinedElement(refElement);
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

    final Set<RefModule> modules = new HashSet<RefModule>();
    for (InspectionTool tool : tools) {
      final Set<RefModule> problems = tool.getModuleProblems();
      if (problems != null) {
        modules.addAll(problems);
      }
    }

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
