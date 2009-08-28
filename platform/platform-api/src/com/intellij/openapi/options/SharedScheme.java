package com.intellij.openapi.options;

public class SharedScheme<E extends ExternalizableScheme> {
  private final String myUserName;
  private final String myDescription;
  private final E myScheme;

  public SharedScheme(final String userName, final String description, final E scheme) {
    myUserName = userName;
    myDescription = description;
    myScheme = scheme;
  }

  public String getUserName() {
    return myUserName;
  }

  public String getDescription() {
    return myDescription;
  }

  public E getScheme() {
    return myScheme;
  }
}
