/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.cvsIntegration;

/**
 * author: lesya
 */
public class CvsModule {
  private final String myPathInCvs;
  private final CvsRepository myRepository;
  private final boolean myIsFile;
  private final String myRevision;

  public CvsModule(CvsRepository repository, String pathInCvs, boolean isFile, String revision) {
    myPathInCvs = pathInCvs;
    myRepository = repository;
    myIsFile = isFile;
    myRevision = revision;
  }

  public CvsModule(CvsRepository repository, String pathInCvs, boolean isFile) {
    this(repository, pathInCvs, isFile, null);
  }

  public String getPathInCvs() {
    return myPathInCvs;
  }

  public CvsRepository getRepository() {
    return myRepository;
  }

  public boolean isFile() {
    return myIsFile;
  }

  public String getRevision() {
    return myRevision;
  }
}
