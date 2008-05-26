package com.intellij.openapi.options;

import com.intellij.openapi.components.RoamingType;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface StreamProvider {
  StreamProvider DEFAULT = new StreamProvider(){
    public void saveContent(final String fileSpec, final Document content, final RoamingType roamingType) throws IOException {

    }

    public Document loadDocument(final String fileSpec, final RoamingType roamingType) throws IOException, JDOMException {
      return null;
    }

    public String[] listSubFiles(final String fileSpec) {
      return new String[0];
    }

    public void deleteFile(final String fileSpec, final RoamingType roamingType) {

    }
  };

  void saveContent(String fileSpec, Document content, final RoamingType roamingType) throws IOException;

  @Nullable
  Document loadDocument(final String fileSpec, final RoamingType roamingType) throws IOException, JDOMException;

  String[] listSubFiles(final String fileSpec);

  void deleteFile(final String fileSpec, final RoamingType roamingType);
}
