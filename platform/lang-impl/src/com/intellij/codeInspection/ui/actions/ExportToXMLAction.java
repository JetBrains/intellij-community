// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportToXMLAction extends ExportInspectionResultsActionBase implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ExportToXMLAction.class);
  @NonNls private static final String ROOT = "root";
  @NonNls private static final String AGGREGATE = "_aggregate";

  public ExportToXMLAction() {
    super("XML", "Exports inspection results to XML", null, true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);
    final InspectionResultsView view = getView(e);

    if (view == null) {
      return;
    }
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(view.getProject());
    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;

    ApplicationManager.getApplication().invokeLater(() -> {
      final Runnable exportRunnable = () -> ApplicationManager.getApplication().runReadAction(() -> dump2xml(view, outputDirectoryName));

      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        exportRunnable, InspectionsBundle.message("inspection.generating.xml.progress.title"), true, view.getProject());
    });
  }

  private static void dump2xml(InspectionResultsView view, final String outputDirectoryName) {
    try {
      final File outputDir = new File(outputDirectoryName);
      if (!outputDir.exists() && !outputDir.mkdirs()) {
        throw new IOException("Cannot create \'" + outputDir + "\'");
      }
      final InspectionTreeNode root = view.getTree().getRoot();
      final Exception[] ex = new Exception[1];

      final Set<String> visitedTools = new THashSet<>();

      Format format = JDOMUtil.createFormat("\n");
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

      TreeUtil.treeNodeTraverser(root).traverse().processEach(node -> {
        if (node instanceof InspectionNode) {
          InspectionNode toolNode = (InspectionNode)node;
          if (toolNode.isExcluded()) return true;

          InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
          if (!visitedTools.add(toolNode.getToolWrapper().getShortName())) return true;

          String name = toolWrapper.getShortName();
          try (XmlWriterWrapper reportWriter = new XmlWriterWrapper(view.getProject(), outputDirectoryName, name,
                                                                    xmlOutputFactory, format,
                                                                    GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
               XmlWriterWrapper aggregateWriter = new XmlWriterWrapper(view.getProject(), outputDirectoryName, name + AGGREGATE,
                                                                       xmlOutputFactory, format, ROOT)) {
            reportWriter.checkOpen();

            for (InspectionToolPresentation presentation : getPresentationsFromAllScopes(view, toolNode)) {
              presentation.exportResults(reportWriter::writeElement, presentation::isExcluded, presentation::isExcluded);
              if (presentation instanceof AggregateResultsExporter) {
                ((AggregateResultsExporter)presentation).exportAggregateResults(aggregateWriter::writeElement);
              }
            }
          }
          catch (XmlWriterWrapperException e) {
            Throwable cause = e.getCause();
            ex[0] = cause instanceof Exception ? (Exception)cause : e;
          }
        }
        return true;
      });
      if (ex[0] != null) {
        throw ex[0];
      }
      final Element element = new Element(InspectionApplication.INSPECTIONS_NODE);
      final String profileName = view.getCurrentProfileName();
      if (profileName != null) {
        element.setAttribute(InspectionApplication.PROFILE, profileName);
      }
      JDOMUtil.write(element,
                     new File(outputDirectoryName, InspectionApplication.DESCRIPTIONS + InspectionApplication.XML_EXTENSION),
                     CodeStyle.getDefaultSettings().getLineSeparator());
    }
    catch (Exception e) {
      LOG.error(e);
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(view, e.getMessage()));
    }
  }

  @NotNull
  public static BufferedWriter getWriter(String outputDirectoryName, String name) throws FileNotFoundException {
    File file = getInspectionResultFile(outputDirectoryName, name);
    FileUtil.createParentDirs(file);
    return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), CharsetToolkit.UTF8_CHARSET));
  }

  @NotNull
  public static File getInspectionResultFile(String outputDirectoryName, String name) {
    return new File(outputDirectoryName, name + InspectionApplication.XML_EXTENSION);
  }

  @NotNull
  private static Collection<InspectionToolPresentation> getPresentationsFromAllScopes(
    @NotNull InspectionResultsView view, @NotNull InspectionNode node) {
    final InspectionToolWrapper wrapper = node.getToolWrapper();
    Stream<InspectionToolWrapper> wrappers;
    if (view.getCurrentProfileName() == null) {
      wrappers = Stream.of(wrapper);
    }
    else {
      final String shortName = wrapper.getShortName();
      final GlobalInspectionContextImpl context = view.getGlobalInspectionContext();
      final Tools tools = context.getTools().get(shortName);
      if (tools != null) {   //dummy entry points tool
        wrappers = tools.getTools().stream().map(ScopeToolState::getTool);
      }
      else {
        wrappers = Stream.empty();
      }
    }
    return wrappers.map(w -> view.getGlobalInspectionContext().getPresentation(w)).collect(Collectors.toList());
  }

  private static class XmlWriterWrapper implements Closeable {
    private final Project myProject;
    private final String myOutputDirectoryName;
    private final String myName;
    private final XMLOutputFactory myFactory;
    private final Format myFormat;
    private final String myRootTagName;

    private XMLStreamWriter myXmlWriter;
    private Writer myFileWriter;

    XmlWriterWrapper(@NotNull Project project,
                     @NotNull String outputDirectoryName,
                     @NotNull String name,
                     @NotNull XMLOutputFactory factory,
                     @NotNull Format format,
                     @NotNull String rootTagName) {
      myProject = project;
      myOutputDirectoryName = outputDirectoryName;
      myName = name;
      myFactory = factory;
      myFormat = format;
      myRootTagName = rootTagName;
    }

    void writeElement(@NotNull Element element) {
      try {
        checkOpen();

        myXmlWriter.writeCharacters(myFormat.getLineSeparator() + myFormat.getIndent());
        myXmlWriter.flush();

        JbXmlOutputter.collapseMacrosAndWrite(element, myProject, myFileWriter);
        myFileWriter.flush();
      }
      catch (XMLStreamException | IOException e) {
        throw new XmlWriterWrapperException(e);
      }
    }

    void checkOpen() {
      if (myXmlWriter == null) {
        myFileWriter = openFile(myOutputDirectoryName, myName);
        myXmlWriter = startWritingXml(myFileWriter);
      }
    }

    @Override
    public void close() {
      if (myXmlWriter != null) {
        try {
          endWritingXml(myXmlWriter);
        }
        finally {
          myXmlWriter = null;

          try {
            closeFile(myFileWriter);
          }
          finally {
            myFileWriter = null;
          }
        }
      }
    }

    @NotNull
    private static Writer openFile(@NotNull String outputDirectoryName, @NotNull String name) {
      try {
        return getWriter(outputDirectoryName, name);
      }
      catch (FileNotFoundException e) {
        throw new XmlWriterWrapperException(e);
      }
    }

    private static void closeFile(@NotNull Writer fileWriter) {
      try {
        fileWriter.close();
      }
      catch (IOException e) {
        throw new XmlWriterWrapperException(e);
      }
    }

    @NotNull
    private XMLStreamWriter startWritingXml(@NotNull Writer fileWriter) {
      try {
        XMLStreamWriter xmlWriter = myFactory.createXMLStreamWriter(fileWriter);
        xmlWriter.writeStartElement(myRootTagName);
        return xmlWriter;
      }
      catch (XMLStreamException e) {
        throw new XmlWriterWrapperException(e);
      }
    }

    private void endWritingXml(@NotNull XMLStreamWriter xmlWriter) {
      try {
        try {
          xmlWriter.writeCharacters(myFormat.getLineSeparator());
          xmlWriter.writeEndElement();
          xmlWriter.flush();
        }
        finally {
          xmlWriter.close();
        }
      }
      catch (XMLStreamException e) {
        throw new XmlWriterWrapperException(e);
      }
    }
  }

  private static class XmlWriterWrapperException extends RuntimeException {
    private XmlWriterWrapperException(Throwable cause) {
      super(cause.getMessage(), cause);
    }
  }
}
