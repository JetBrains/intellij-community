/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2002
 * Time: 9:50:29 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.export;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.HTMLComposerImpl;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HTMLExporter {
  private final String myRootFolder;
  private int myFileCounter;
  private final Map<RefEntity,String> myElementToFilenameMap;
  private final HTMLComposerImpl myComposer;
  private final Set<RefEntity> myGeneratedReferences;
  private final Set<RefEntity> myGeneratedPages;

  public HTMLExporter(String rootFolder, HTMLComposerImpl composer) {
    myRootFolder = rootFolder;
    myElementToFilenameMap = new HashMap<RefEntity, String>();
    myFileCounter = 0;
    myComposer = composer;
    myGeneratedPages = new HashSet<RefEntity>();
    myGeneratedReferences = new HashSet<RefEntity>();
  }

  public void createPage(RefEntity element) throws IOException {
    final String currentFileName = fileNameForElement(element);
    StringBuffer buf = new StringBuffer("<html><body>");
    appendNavBar(buf, element);
    myComposer.composeWithExporter(buf, element, this);
    buf.append("</body></html>");
    writeFileImpl(myRootFolder, currentFileName, buf);
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

  public static void writeFileImpl(String folder, @NonNls String fileName, CharSequence buf) throws IOException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String fullPath = folder + File.separator + fileName;

    if (indicator != null) {
      ProgressManager.checkCanceled();
      indicator.setText(InspectionsBundle.message("inspection.export.generating.html.for", fullPath));
    }

    FileWriter writer = null;
    try {
      File folderFile = new File(folder);
      folderFile.mkdirs();
      new File(fullPath).getParentFile().mkdirs();
      writer = new FileWriter(fullPath);
      writer.write(buf.toString().toCharArray());
    }
    finally {
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

  public void generateReferencedPages() throws IOException {
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
