package org.jetbrains.jps.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/4/11
 */
public class SdkLibrary extends GlobalLibrary {
  private final String myTypeName;
  @Nullable private final String myVersion;
  private final String myHomePath;
  @Nullable
  private final String myAdditionalDataXml;

  public SdkLibrary(String name, String typeName, @Nullable String version, String homePath, List<String> paths,
                    @Nullable String additionalDataXml) {
    super(name, paths);
    myTypeName = typeName;
    myVersion = version;
    myHomePath = homePath;
    myAdditionalDataXml = additionalDataXml;
  }

  @Nullable
  public String getVersion() {
    return myVersion;
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
