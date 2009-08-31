package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class ComponentManagerSettingsImpl implements ComponentManagerSettings {
  protected final Document myDocument;
  protected final File myFile;
  protected final Element myRootElement;

  protected ComponentManagerSettingsImpl(File file) throws CannotConvertException {
    myDocument = JDomConvertingUtil.loadDocument(file);
    myFile = file;
    myRootElement = myDocument.getRootElement();
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public Element getRootElement() {
    return myRootElement;
  }

  public Element getComponentElement(@NotNull @NonNls String componentName) {
    return JDomConvertingUtil.findComponent(myRootElement, componentName);
  }

  public void save() throws IOException {
    JDOMUtil.writeDocument(myDocument, myFile, SystemProperties.getLineSeparator());
  }
}
