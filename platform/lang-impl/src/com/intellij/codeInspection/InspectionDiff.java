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
 * Date: Jun 21, 2002
 * Time: 7:36:28 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class InspectionDiff {
  private static HashMap<String, ArrayList<Element>> ourFileToProblem;
    @NonNls
    private static final String FILE_ELEMENT = "file";
    @NonNls
    private static final String CLASS_ELEMENT = "class";
    @NonNls
    private static final String FIELD_ELEMENT = "field";
    @NonNls
    private static final String METHOD_ELEMENT = "method";
    @NonNls
    private static final String CONSTRUCTOR_ELEMENT = "constructor";
    @NonNls
    private static final String INTERFACE_ELEMENT = "interface";
    @NonNls
    private static final String PROBLEM_CLASS_ELEMENT = "problem_class";
    @NonNls
    private static final String DESCRIPTION_ELEMENT = "description";

    public static void main(String[] args) {
      if (args.length != 3 && args.length != 2) {
        System.out.println(InspectionsBundle.message("inspection.diff.format.error"));
      }

      String oldPath = args[0];
      String newPath = args[1];
      String outPath = args.length == 3 ? args[2] : null;

      final File oldResults = new File(oldPath);
      final File newResults = new File(newPath);
      if (oldResults.isDirectory() && newResults.isDirectory()) {
        final File[] old = oldResults.listFiles();
        final File[] results = newResults.listFiles();
        for (File result : results) {
          final String inspectionName = result.getName();
          boolean found = false;
          for (File oldFile : old) {
            if (oldFile.getName().equals(inspectionName)) {
              writeInspectionDiff(oldFile.getPath(), result.getPath(), outPath);
              found = true;
              break;
            }
          }
          if (!found) {
            writeInspectionDiff(null, result.getPath(), outPath);
          }
        }
      }
    }

  private static void writeInspectionDiff(final String oldPath, final String newPath, final String outPath) {
    try {
      InputStream oldStream = oldPath != null ? new BufferedInputStream(new FileInputStream(oldPath)) : null;
      InputStream newStream = new BufferedInputStream(new FileInputStream(newPath));

      Document oldDoc = oldStream != null ? JDOMUtil.loadDocument(oldStream) : null;
      Document newDoc = JDOMUtil.loadDocument(newStream);

      OutputStream outStream = System.out;
      if (outPath != null) {
        outStream = new BufferedOutputStream(new FileOutputStream(outPath + File.separator + new File(newPath).getName()));
      }

      Document delta = createDelta(oldDoc, newDoc);
      JDOMUtil.writeDocument(delta, outStream, "\n");
      if (outStream != System.out) {
        outStream.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Document createDelta(@Nullable Document oldDoc, Document newDoc) {
    Element newRoot = newDoc.getRootElement();

    ourFileToProblem = new HashMap<>();
    List newProblems = newRoot.getChildren("problem");
    for (final Object o : newProblems) {
      Element newProblem = (Element)o;
      addProblem(newProblem);
    }

    if (oldDoc != null) {
      List oldProblems = oldDoc.getRootElement().getChildren("problem");
      for (final Object o : oldProblems) {
        Element oldProblem = (Element)o;
        if (!removeIfEquals(oldProblem)) {
          addProblem(oldProblem);
        }
      }
    }

    Element root = new Element("problems");
    Document delta = new Document(root);

    for (ArrayList<Element> fileList : ourFileToProblem.values()) {
      if (fileList != null) {
        for (Element element : fileList) {
          root.addContent((Element)element.clone());
        }
      }
    }

    return delta;
  }

  private static boolean removeIfEquals(Element problem) {
    String fileName = problem.getChildText(FILE_ELEMENT);
    ArrayList<Element> problemList = ourFileToProblem.get(fileName);
    if (problemList != null) {
      Element[] problems = problemList.toArray(new Element[problemList.size()]);
      for (Element toCheck : problems) {
        if (equals(problem, toCheck)) return problemList.remove(toCheck);
      }
    }
    return false;
  }

  private static void addProblem(Element problem) {
    String fileName = problem.getChildText(FILE_ELEMENT);
    ArrayList<Element> problemList = ourFileToProblem.get(fileName);
    if (problemList == null) {
      problemList = new ArrayList<>();
      ourFileToProblem.put(fileName, problemList);
    }
    problemList.add(problem);
  }

  private static boolean equals(Element oldProblem, Element newProblem) {
    if (!Comparing.equal(oldProblem.getChildText(CLASS_ELEMENT), newProblem.getChildText(CLASS_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(FIELD_ELEMENT), newProblem.getChildText(FIELD_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(METHOD_ELEMENT), newProblem.getChildText(METHOD_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(CONSTRUCTOR_ELEMENT), newProblem.getChildText(CONSTRUCTOR_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(INTERFACE_ELEMENT), newProblem.getChildText(INTERFACE_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(PROBLEM_CLASS_ELEMENT), newProblem.getChildText(PROBLEM_CLASS_ELEMENT))) return false;
    if (!Comparing.equal(oldProblem.getChildText(DESCRIPTION_ELEMENT), newProblem.getChildText(DESCRIPTION_ELEMENT))) return false;

    return true;
  }
}
