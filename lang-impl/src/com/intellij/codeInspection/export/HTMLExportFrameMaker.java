/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 21, 2002
 * Time: 1:16:43 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.export;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationNamesInfo;

import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

public class HTMLExportFrameMaker {
  private final String myRootFolder;
  private Project myProject;
  private final ArrayList myInspectionTools;

  public HTMLExportFrameMaker(String rootFolder, Project project) {
    myRootFolder = rootFolder;
    myProject = project;
    myInspectionTools = new ArrayList();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void start() {
    StringBuffer buf = new StringBuffer();
    buf.append("<HTML><BODY></BODY></HTML>");
    HTMLExporter.writeFile(myRootFolder, "empty.html", buf, myProject);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void done() {
    StringBuffer buf = new StringBuffer();

    for (int i = 0; i < myInspectionTools.size(); i++) {
      InspectionTool tool = (InspectionTool) myInspectionTools.get(i);
      buf.append("<A HREF=\"");
      buf.append(tool.getFolderName());
      buf.append("-index.html\">");
      buf.append(tool.getDisplayName());
      buf.append("</A><BR>");
    }

    HTMLExporter.writeFile(myRootFolder, "index.html", buf, myProject);
  }

  public void startInspection(InspectionTool tool) {
    myInspectionTools.add(tool);
    @NonNls StringBuffer buf = new StringBuffer();
    buf.append("<HTML><HEAD><TITLE>");
    buf.append(ApplicationNamesInfo.getInstance().getFullProductName());
    buf.append(InspectionsBundle.message("inspection.export.title"));
    buf.append("</TITLE></HEAD>");
    buf.append("<FRAMESET cols=\"30%,70%\"><FRAMESET rows=\"30%,70%\">");
    buf.append("<FRAME src=\"");
    buf.append(tool.getFolderName());
    buf.append("/index.html\" name=\"inspectionFrame\">");
    buf.append("<FRAME src=\"empty.html\" name=\"packageFrame\">");
    buf.append("</FRAMESET>");
    buf.append("<FRAME src=\"empty.html\" name=\"elementFrame\">");
    buf.append("</FRAMESET></BODY></HTML");

    HTMLExporter.writeFile(myRootFolder, tool.getFolderName() + "-index.html", buf, myProject);
  }

  public String getRootFolder() {
    return myRootFolder;
  }
}
