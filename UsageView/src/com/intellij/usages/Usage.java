package com.intellij.usages;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.pom.Navigatable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:19:07 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Usage extends Navigatable {
  UsagePresentation getPresentation();
  boolean isValid();
  boolean isReadOnly();

  FileEditorLocation getLocation();

  void selectInEditor();
  void highlightInEditor();
}
