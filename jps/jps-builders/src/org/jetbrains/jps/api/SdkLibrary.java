package org.jetbrains.jps.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/4/11
 */
public class SdkLibrary extends GlobalLibrary {

  private final String myTypeName;
  private final String myHomePath;
  @Nullable
  private final String myAdditionalDataXml;

  public SdkLibrary(String name, final String typeName, String homePath, List<String> paths) {
    this(name, typeName, homePath, paths, null);
  }

  public SdkLibrary(String name, String typeName, String homePath, List<String> paths, @Nullable String additionalDataXml) {
    super(name, paths);
    myTypeName = typeName;
    myHomePath = homePath;
    myAdditionalDataXml = additionalDataXml;
  }

  public String getTypeName() {
    return myTypeName;
  }

  public String getHomePath() {
    return myHomePath;
  }

  @Nullable
  public String getAdditionalDataXml() {
    return myAdditionalDataXml;
  }
}
