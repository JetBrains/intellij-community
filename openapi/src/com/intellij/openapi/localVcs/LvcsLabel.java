/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;



/**
 * @author Mike
 */
public interface LvcsLabel extends Comparable<LvcsLabel>{
  byte TYPE_BEFORE_ACTION = 1;
  byte TYPE_AFTER_ACTION = 2;

  byte TYPE_OTHER = 3;
  byte TYPE_TESTS_SUCCESSFUL = 4;
  byte TYPE_TESTS_FAILED = 5;

  int getType();
  String getName();
  String getPath();
  long getDate();
  String getAction();
  int getVersionId();


  int compareTo(LvcsRevision revision);
}
