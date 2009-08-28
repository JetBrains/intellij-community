package com.intellij.codeInsight.intention.impl.config;

import java.io.IOException;

/**
 * @author yole
 */
public class PlainTextDescriptor implements TextDescriptor {
  private final String myText;
  private final String myFileName;

  public PlainTextDescriptor(final String text, final String fileName) {
    myText = text;
    myFileName = fileName;
  }

  public String getText() throws IOException {
    return myText;
  }

  public String getFileName() {
    return myFileName;
  }
}
