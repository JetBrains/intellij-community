package com.intellij.openapi.options;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.JDOMException;

import java.io.IOException;
import java.io.File;

public interface SchemeProcessor<T extends Scheme> {
  T readScheme(Document schemeContent, final File file) throws InvalidDataException, IOException, JDOMException;
  Document writeScheme(T scheme) throws WriteExternalException;
  void showReadErrorMessage(Exception e, final String schemeName, final String filePath);
  void showWriteErrorMessage(Exception e, final String schemeName, final String filePath);
  boolean shouldBeSaved(T scheme);

}
