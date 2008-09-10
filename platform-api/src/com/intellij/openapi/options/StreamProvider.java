package com.intellij.openapi.options;

import com.intellij.openapi.components.RoamingType;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface StreamProvider {
  StreamProvider DEFAULT = new StreamProvider(){
    public void saveContent(final String fileSpec, final byte[] content, final RoamingType roamingType) throws IOException {

    }

    public byte[] loadContent(final String fileSpec, final RoamingType roamingType) throws IOException, JDOMException {
      return null;
    }

    public String[] listSubFiles(final String fileSpec) {
      return new String[0];
    }

    public void deleteFile(final String fileSpec, final RoamingType roamingType) {

    }

    public boolean isEnabled() {
      return false;
    }
  };

  void saveContent(String fileSpec, byte[] content, final RoamingType roamingType) throws IOException;

  @Nullable
  byte[] loadContent(final String fileSpec, final RoamingType roamingType) throws IOException, JDOMException;

  String[] listSubFiles(final String fileSpec);

  void deleteFile(final String fileSpec, final RoamingType roamingType);

  boolean isEnabled();
}
