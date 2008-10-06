package com.intellij.openapi.options;

import com.intellij.openapi.components.RoamingType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface StreamProvider {
  StreamProvider DEFAULT = new StreamProvider(){
    public void saveContent(final String fileSpec, final InputStream content, final long size, final RoamingType roamingType) throws IOException {

    }

    public InputStream loadContent(final String fileSpec, final RoamingType roamingType) throws IOException {
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

  void saveContent(String fileSpec, InputStream content, final long size, final RoamingType roamingType) throws IOException;

  @Nullable
  InputStream loadContent(final String fileSpec, final RoamingType roamingType) throws IOException;

  String[] listSubFiles(final String fileSpec);

  void deleteFile(final String fileSpec, final RoamingType roamingType);

  boolean isEnabled();
}
