/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2002
 * Time: 9:50:29 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.export;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class HTMLExporter {
  private final String myRootFolder;
  private Project myProject;
  private int myFileCounter;
  private final HashMap<RefElement,String> myElementToFilenameMap;
  private final HTMLComposer myComposer;
  private final HashSet<RefElement> myGeneratedReferences;
  private final HashSet<RefElement> myGeneratedPages;

  public HTMLExporter(String rootFolder, HTMLComposer composer, Project project) {
    myRootFolder = rootFolder;
    myProject = project;
    myElementToFilenameMap = new HashMap<RefElement, String>();
    myFileCounter = 0;
    myComposer = composer;
    myGeneratedPages = new HashSet<RefElement>();
    myGeneratedReferences = new HashSet<RefElement>();
  }

  public void createPage(RefElement element) {
    final String currentFileName = fileNameForElement(element);
    StringBuffer buf = new StringBuffer();
    appendNavBar(buf, element);
    myComposer.composeWithExporter(buf, element, this);
    writeFile(myRootFolder, currentFileName, buf, myProject);
    myGeneratedPages.add(element);
  }

  private void appendNavBar(@NonNls final StringBuffer buf, RefElement element) {
    buf.append("<a href=\"../index.html\" target=\"_top\">");
    buf.append(InspectionsBundle.message("inspection.export.inspections.link.text"));
    buf.append("</a>  ");
    myComposer.appendElementReference(buf, element, InspectionsBundle.message("inspection.export.open.source.link.text"), "_blank");
    buf.append("<hr>");
  }

  public static void writeFile(String folder, @NonNls String fileName, StringBuffer buf, Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    String fullPath = folder + File.separator + fileName;

    if (indicator != null) {
      ProgressManager.getInstance().checkCanceled();
      indicator.setText(InspectionsBundle.message("inspection.export.generating.html.for", fullPath));
    }

    FileWriter writer = null;
    try {
      File folderFile = new File(folder);
      folderFile.mkdirs();
      writer = new FileWriter(fullPath);
      writer.write(buf.toString().toCharArray());
    } catch (IOException e) {
      Messages.showMessageDialog(
        project,
        InspectionsBundle.message("inspection.export.error.writing.to", fullPath),
        InspectionsBundle.message("inspection.export.results.error.title"),
        Messages.getErrorIcon()
      );
      throw new ProcessCanceledException();
    } finally {
      if (writer != null) {
        try {
          writer.close();
        }
        catch (IOException e) {
          //Cannot do anything in case of exception
        }
      }
    }
  }

  public String getURL(RefElement element) {
    myGeneratedReferences.add(element);
    return fileNameForElement(element);
  }

  private String fileNameForElement(RefElement element) {
    @NonNls String fileName = myElementToFilenameMap.get(element);

    if (fileName == null) {
      fileName = "e" + Integer.toString(++myFileCounter) + ".html";
      myElementToFilenameMap.put(element, fileName);
    }

    return fileName;
  }

  private Set<RefElement> getReferencesWithoutPages() {
    HashSet<RefElement> result = new HashSet<RefElement>();
    for (RefElement refElement : myGeneratedReferences) {
      if (!myGeneratedPages.contains(refElement)) {
        result.add(refElement);
      }
    }

    return result;
  }

  public void generateReferencedPages() {
    Set<RefElement> extras = getReferencesWithoutPages();
    while (extras.size() > 0) {
      for (RefElement refElement : extras) {
        createPage(refElement);
      }
      extras = getReferencesWithoutPages();
    }
  }

  public String getRootFolder() {
    return myRootFolder;
  }
}
