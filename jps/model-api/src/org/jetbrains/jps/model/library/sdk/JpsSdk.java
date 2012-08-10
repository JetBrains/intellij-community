package org.jetbrains.jps.model.library.sdk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibrary;

/**
 * @author nik
 */
public interface JpsSdk<P extends JpsElement> extends JpsElement {

  @NotNull
  JpsLibrary getParent();

  String getHomePath();

  void setHomePath(String homePath);

  String getVersionString();

  void setVersionString(String versionString);

  JpsSdkType<P> getSdkType();

  P getSdkProperties();

  JpsSdkReference<P> createReference();
}
