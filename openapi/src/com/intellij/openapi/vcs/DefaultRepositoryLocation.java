package com.intellij.openapi.vcs;

/**
 * @author yole
 */
public class DefaultRepositoryLocation implements RepositoryLocation {
  private String myURL;

  public DefaultRepositoryLocation(final String URL) {
    myURL = URL;
  }

  public String getURL() {
    return myURL;
  }

  public String toString() {
    return myURL;
  }

  public String toPresentableString() {
    return myURL;
  }
}
