package com.intellij.usageView;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class UsageTreeColorsScheme implements ApplicationComponent, JDOMExternalizable{
  private EditorColorsScheme myColorsScheme;
  private EditorColorsManager myEditorColorsManager;

  public UsageTreeColorsScheme(EditorColorsManager editorColorsManager) {
    myEditorColorsManager = editorColorsManager;
  }

  public static UsageTreeColorsScheme getInstance() {
    return ApplicationManager.getApplication().getComponent(UsageTreeColorsScheme.class);
  }

  public String getComponentName() {
    return "FindViewColorsScheme";
  }

  public EditorColorsScheme getScheme() {
    return myColorsScheme;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    if (myColorsScheme == null){
      myColorsScheme = (EditorColorsScheme) myEditorColorsManager.getScheme("Default").clone();
    }
    myColorsScheme.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }
}
