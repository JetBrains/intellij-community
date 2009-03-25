package com.intellij.testFramework;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;

/**
 * @author Mike
 */
public class PsiTestData implements JDOMExternalizable {
  public String TEXT_FILE = "";
  private String myText;

  public String getTextFile() {
    return TEXT_FILE;
  }

  public String getText() {
    return myText;
  }

  public void loadText(String root) throws IOException{
    String fileName = root + "/" + TEXT_FILE;
    myText = new String(FileUtil.loadFileText(new File(fileName)));
    myText = StringUtil.convertLineSeparators(myText);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
