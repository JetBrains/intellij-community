// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.StAXStreamOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class ExportHTMLAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ExportHTMLAction.class);
  private final InspectionResultsView myView;
  @NonNls private static final String ROOT = "root";
  @NonNls private static final String AGGREGATE = "_aggregate";
  @NonNls private static final String HTML = "HTML";
  @NonNls private static final String XML = "XML";

  public ExportHTMLAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.export.html"), null, AllIcons.ToolbarDecorator.Export);
    myView = view;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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
      final Exception[] ex = new Exception[1];

      final Set<InspectionToolWrapper> visitedWrappers = new THashSet<>();
      final Element aggregateRoot = new Element(ROOT);

      Format format = JDOMUtil.createFormat("\n");
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

      TreeUtil.treeNodeTraverser(root).traverse().processEach(node -> {
        if (node instanceof InspectionNode) {
          InspectionNode toolNode = (InspectionNode)node;
          if (toolNode.isExcluded()) return true;

          InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
          if (!visitedWrappers.add(toolWrapper)) return true;

          String name = toolWrapper.getShortName();
          try (BufferedWriter fileWriter = getWriter(outputDirectoryName, name)) {
            XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(fileWriter);
            xmlWriter.writeStartElement(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);

            final Set<InspectionToolWrapper> toolWrappers = getWorkedTools(toolNode);
            for (InspectionToolWrapper wrapper : toolWrappers) {
              InspectionToolPresentation presentation = myView.getGlobalInspectionContext().getPresentation(wrapper);
              presentation.exportResults(p -> {
                try {
                  xmlWriter.writeCharacters(format.getLineSeparator() + format.getIndent());
                  xmlWriter.flush();
                  JbXmlOutputter.collapseMacrosAndWrite(p, myView.getProject(), fileWriter);
                  fileWriter.flush();
                }
                catch (IOException | XMLStreamException e) {
                  throw new RuntimeException(e);
                }
              }, presentation::isExcluded, presentation::isExcluded);
              presentation.exportAggregateResults(aggregateRoot, presentation::isExcluded, presentation::isExcluded);
            }
            writeDocument(aggregateRoot, outputDirectoryName, name + AGGREGATE);
            xmlWriter.writeCharacters(format.getLineSeparator());
            xmlWriter.writeEndElement();
            xmlWriter.flush();
          }
          catch (IOException | XMLStreamException e) {
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
                     CodeStyle.getDefaultSettings().getLineSeparator());
    }
    catch (Exception e) {
      LOG.error(e);
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myView, e.getMessage()));
    }
  }

  private static BufferedWriter getWriter(String outputDirectoryName, String name) throws FileNotFoundException {
    File file = new File(outputDirectoryName, name + InspectionApplication.XML_EXTENSION);
    FileUtil.createParentDirs(file);
    return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), CharsetToolkit.UTF8_CHARSET));
  }

  private static void writeDocument(@NotNull Element problems, String outputDirectoryName, String name) throws IOException {
    if (problems.getContentSize() != 0) {
      JDOMUtil.writeDocument(new Document(problems),
                             outputDirectoryName + File.separator + name + InspectionApplication.XML_EXTENSION,
                             CodeStyle.getDefaultSettings().getLineSeparator());
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
