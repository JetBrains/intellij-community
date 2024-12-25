// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

final class InspectionDiff {
  private static HashMap<String, ArrayList<Element>> ourFileToProblem;
  private static final @NonNls String FILE_ELEMENT = "file";
  private static final @NonNls String CLASS_ELEMENT = "class";
  private static final @NonNls String FIELD_ELEMENT = "field";
  private static final @NonNls String METHOD_ELEMENT = "method";
  private static final @NonNls String CONSTRUCTOR_ELEMENT = "constructor";
  private static final @NonNls String INTERFACE_ELEMENT = "interface";
  private static final @NonNls String PROBLEM_CLASS_ELEMENT = "problem_class";
  private static final @NonNls String DESCRIPTION_ELEMENT = "description";

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

  private static void writeInspectionDiff(String oldPath, String newPath, final String outPath) {
    try {
      Element oldDoc = oldPath == null ? null : JDOMUtil.load(Path.of(oldPath));
      Element newDoc = JDOMUtil.load(Path.of(newPath));

      OutputStream outStream = System.out;
      if (outPath != null) {
        outStream = new BufferedOutputStream(new FileOutputStream(outPath + File.separator + new File(newPath).getName()));
      }

      Document delta = createDelta(oldDoc, newDoc);
      JDOMUtil.writeDocument(delta, outStream, "\n");
      if (outStream != System.out) {
        outStream.close();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static @NotNull Document createDelta(@Nullable Element oldRoot, Element newRoot) {
    ourFileToProblem = new HashMap<>();
    List<Element> newProblems = newRoot.getChildren("problem");
    for (Element problem : newProblems) {
      addProblem(problem);
    }

    if (oldRoot != null) {
      for (Element oldProblem : oldRoot.getChildren("problem")) {
        if (!removeIfEquals(oldProblem)) {
          addProblem(oldProblem);
        }
      }
    }

    Element root = new Element(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
    Document delta = new Document(root);

    for (ArrayList<Element> fileList : ourFileToProblem.values()) {
      if (fileList != null) {
        for (Element element : fileList) {
          root.addContent(element.clone());
        }
      }
    }

    return delta;
  }

  private static boolean removeIfEquals(Element problem) {
    String fileName = problem.getChildText(FILE_ELEMENT);
    ArrayList<Element> problemList = ourFileToProblem.get(fileName);
    if (problemList != null) {
      Element[] problems = problemList.toArray(new Element[0]);
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
    if (!Objects.equals(oldProblem.getChildText(CLASS_ELEMENT), newProblem.getChildText(CLASS_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(FIELD_ELEMENT), newProblem.getChildText(FIELD_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(METHOD_ELEMENT), newProblem.getChildText(METHOD_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(CONSTRUCTOR_ELEMENT), newProblem.getChildText(CONSTRUCTOR_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(INTERFACE_ELEMENT), newProblem.getChildText(INTERFACE_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(PROBLEM_CLASS_ELEMENT), newProblem.getChildText(PROBLEM_CLASS_ELEMENT))) return false;
    if (!Objects.equals(oldProblem.getChildText(DESCRIPTION_ELEMENT), newProblem.getChildText(DESCRIPTION_ELEMENT))) return false;

    return true;
  }
}
