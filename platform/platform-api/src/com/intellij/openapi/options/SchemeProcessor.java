package com.intellij.openapi.options;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.JDOMException;

import java.io.IOException;

public interface SchemeProcessor<T extends ExternalizableScheme> {
  T readScheme(Document schemeContent) throws InvalidDataException, IOException, JDOMException;
  Document writeScheme(T scheme) throws WriteExternalException;

  boolean shouldBeSaved(T scheme);
  void initScheme(T scheme);

  void onSchemeAdded(T scheme);
  void onSchemeDeleted(T scheme);

  void onCurrentSchemeChanged(final Scheme oldCurrentScheme);
}
