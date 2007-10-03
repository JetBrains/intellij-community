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

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InspectionDiff {
  private static HashMap ourFileToProblem;
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
        outStream = new BufferedOutputStream(new FileOutputStream(outPath + File.separator + new File(oldPath).getName()));
      }

      Document delta = createDelta(oldDoc, newDoc);
      JDOMUtil.writeDocument(delta, outStream, "\n");
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Document createDelta(Document oldDoc, Document newDoc) {
    Element oldRoot = oldDoc.getRootElement();
    Element newRoot = newDoc.getRootElement();


    ourFileToProblem = new HashMap();
    List newProblems = newRoot.getChildren("problem");
    for (Iterator iterator = newProblems.iterator(); iterator.hasNext();) {
      Element newProblem = (Element) iterator.next();
      addProblem(newProblem);
    }

    List oldProblems = oldRoot.getChildren("problem");
    for (Iterator iterator = oldProblems.iterator(); iterator.hasNext();) {
      Element oldProblem = (Element) iterator.next();
      removeIfEquals(oldProblem);
    }

    Element root = new Element("problems");
    Document delta = new Document(root);

    for (Iterator iterator = ourFileToProblem.values().iterator(); iterator.hasNext();) {
      ArrayList fileList = (ArrayList) iterator.next();
      if (fileList != null) {
        for (int i = 0; i < fileList.size(); i++) {
          Element element = (Element) fileList.get(i);
          root.addContent((Element) element.clone());
        }
      }
    }

    return delta;
  }

  private static void removeIfEquals(Element problem) {
    String fileName = problem.getChildText(FILE_ELEMENT);
    ArrayList problemList = (ArrayList) ourFileToProblem.get(fileName);
    if (problemList != null) {
      Element[] problems = (Element[]) problemList.toArray(new Element[problemList.size()]);
      for (int i = 0; i < problems.length; i++) {
        Element toCheck = problems[i];
        if (equals(problem, toCheck)) problemList.remove(toCheck);
      }
    }
  }

  private static void addProblem(Element problem) {
    String fileName = problem.getChildText(FILE_ELEMENT);
    ArrayList problemList = (ArrayList) ourFileToProblem.get(fileName);
    if (problemList == null) {
      problemList = new ArrayList();
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
