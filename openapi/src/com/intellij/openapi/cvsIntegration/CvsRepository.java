/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.cvsIntegration;

import com.intellij.openapi.diagnostic.Logger;

/**
 * author: lesya
 */
public class CvsRepository {
  private final String myMethod;
  private final String myUser;
  private final String myHost;
  private final String myRepository;
  private final int myPort;
  private final DateOrRevision myBranch;
  private final String myStringRepresentation;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.cvsIntegration.CvsRepository");

  public CvsRepository(String stringRepresentation,
                       String method,
                       String user,
                       String host,
                       String repository,
                       int port,
                       DateOrRevision branch) {
    LOG.assertTrue(method != null);
    LOG.assertTrue(host != null);
    LOG.assertTrue(repository != null);
    LOG.assertTrue(branch != null);
    LOG.assertTrue(stringRepresentation != null);
    LOG.assertTrue(port > 0);

    myMethod = method;
    myUser = user;
    myHost = host;
    myRepository = repository;
    myPort = port;
    myBranch = branch;
    myStringRepresentation = stringRepresentation;
  }

  public String getMethod() {
    return myMethod;
  }

  public String getUser() {
    return myUser;
  }

  public String getHost() {
    return myHost;
  }

  public String getRepository() {
    return myRepository;
  }

  public int getPort() {
    return myPort;
  }

  public DateOrRevision getDateOrRevision() {
    return myBranch;
  }

  public String getStringRepresentation() {
    return myStringRepresentation;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CvsRepository)) return false;

    final CvsRepository cvsRepository = (CvsRepository)o;

    if (myPort != cvsRepository.myPort) return false;
    if (!myBranch.equals(cvsRepository.myBranch)) return false;
    if (!myHost.equals(cvsRepository.myHost)) return false;
    if (!myMethod.equals(cvsRepository.myMethod)) return false;
    if (!myRepository.equals(cvsRepository.myRepository)) return false;
    if (!myStringRepresentation.equals(cvsRepository.myStringRepresentation)) return false;
    if (myUser != null ? !myUser.equals(cvsRepository.myUser) : cvsRepository.myUser != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myMethod.hashCode();
    result = 29 * result + (myUser != null ? myUser.hashCode() : 0);
    result = 29 * result + myHost.hashCode();
    result = 29 * result + myRepository.hashCode();
    result = 29 * result + myPort;
    result = 29 * result + myBranch.hashCode();
    result = 29 * result + myStringRepresentation.hashCode();
    return result;
  }
}
