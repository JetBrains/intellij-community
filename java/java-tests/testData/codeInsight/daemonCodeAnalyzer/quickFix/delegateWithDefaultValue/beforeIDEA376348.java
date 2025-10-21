// "Generate overloaded constructor with default parameter values" "false"

package com.intellij.openapi.graph.services;

public final class GraphExportService {

  private vola<caret>tile boo(lean myPrintMode = false;
  private volatile boolean myExportMode = false;

  private static final GraphExportService INSTANCE = new GraphExportService();

  public static GraphExportService getInstance() {
    return INSTANCE;
  }

  private GraphExportService() {
  }

  public boolean isPrintMode() {
    return myPrintMode;
  }

  public void setPrintMode(boolean printMode) {
    myPrintMode = printMode;
  }

  public boolean isExportMode() {
    return myExportMode;
  }

  public void setExportMode(boolean exportMode) {
    myExportMode = exportMode;
  }
}
