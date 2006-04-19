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
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
  private final HashMap<RefEntity,String> myElementToFilenameMap;
  private final HTMLComposer myComposer;
  private final HashSet<RefEntity> myGeneratedReferences;
  private final HashSet<RefEntity> myGeneratedPages;

  public HTMLExporter(String rootFolder, HTMLComposer composer, Project project) {
    myRootFolder = rootFolder;
    myProject = project;
    myElementToFilenameMap = new HashMap<RefEntity, String>();
    myFileCounter = 0;
    myComposer = composer;
    myGeneratedPages = new HashSet<RefEntity>();
    myGeneratedReferences = new HashSet<RefEntity>();
  }

  public void createPage(RefEntity element) {
    final String currentFileName = fileNameForElement(element);
    StringBuffer buf = new StringBuffer();
    appendNavBar(buf, element);
    myComposer.composeWithExporter(buf, element, this);
    writeFile(myRootFolder, currentFileName, buf, myProject);
    myGeneratedPages.add(element);
  }

  private void appendNavBar(@NonNls final StringBuffer buf, RefEntity element) {
    buf.append("<a href=\"../index.html\" target=\"_top\">");
    buf.append(InspectionsBundle.message("inspection.export.inspections.link.text"));
    buf.append("</a>  ");
    if (element instanceof RefElement) {
      myComposer.appendElementReference(buf, getURL(element), InspectionsBundle.message("inspection.export.open.source.link.text"), "_blank");
    }
    buf.append("<hr>");
  }

  public static void writeFile(String folder, @NonNls String fileName, StringBuffer buf, final Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String fullPath = folder + File.separator + fileName;

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
      Runnable showError = new Runnable() {
        public void run() {
          Messages.showMessageDialog(
            project,
            InspectionsBundle.message("inspection.export.error.writing.to", fullPath),
            InspectionsBundle.message("inspection.export.results.error.title"),
            Messages.getErrorIcon()
          );
        }
      };
      ApplicationManager.getApplication().invokeLater(showError, ModalityState.NON_MMODAL);
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

  public String getURL(RefEntity element) {
    myGeneratedReferences.add(element);
    return fileNameForElement(element);
  }

  private String fileNameForElement(RefEntity element) {
    @NonNls String fileName = myElementToFilenameMap.get(element);

    if (fileName == null) {
      fileName = "e" + Integer.toString(++myFileCounter) + ".html";
      myElementToFilenameMap.put(element, fileName);
    }

    return fileName;
  }

  private Set<RefEntity> getReferencesWithoutPages() {
    HashSet<RefEntity> result = new HashSet<RefEntity>();
    for (RefEntity refElement : myGeneratedReferences) {
      if (!myGeneratedPages.contains(refElement)) {
        result.add(refElement);
      }
    }

    return result;
  }

  public void generateReferencedPages() {
    Set<RefEntity> extras = getReferencesWithoutPages();
    while (extras.size() > 0) {
      for (RefEntity refElement : extras) {
        createPage(refElement);
      }
      extras = getReferencesWithoutPages();
    }
  }

  public String getRootFolder() {
    return myRootFolder;
  }
}
