/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;


public interface LvcsRevision extends Comparable{
  String PROPERTY_UP_TO_DATE = "upToDate";

  String getName();
  String getAbsolutePath();

  long getDate();
  boolean isDeleted();

  LvcsObject getObject();

  LvcsRevision getNextRevision();
  LvcsRevision getPrevRevision();

  LvcsRevision getParentRevision();

  void setUpToDate(boolean value);
  boolean isUpToDate();

  /**
   * Returns a lable, which is associated with this revision. You can use this label to view the VCS
   * at the moment of revision creation. This label is not visible view LocalVcs.getLabels().
   */
  LvcsLabel getImplicitLabel();

  LvcsRevision findLatestRevision();

  LvcsRevision findNearestPreviousUpToDateRevision();

  RepositoryItem getItem();

  int compareTo(LvcsLabel label);

  int compareTo(LvcsRevision label);

  long getCreationDate();

  int getVersionId();
}
