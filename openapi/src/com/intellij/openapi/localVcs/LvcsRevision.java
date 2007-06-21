/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.localVcs;

import org.jetbrains.annotations.NonNls;

/**
 * @deprecated use LocalHistory instead
 */
public interface LvcsRevision extends Comparable{
  @NonNls String PROPERTY_UP_TO_DATE = "upToDate";

  String getName();
  String getAbsolutePath();

  long getDate();
  boolean isDeleted();
  boolean isPurged();

  int getId();

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

  int compareTo(LvcsLabel label);

  int compareTo(LvcsRevision label);

  long getCreationDate();

  int getLength();

  int getVersionId();

  int getVersion();

  boolean isScheduledForRemoval();

  boolean isTooLong();

  boolean hasContent();
}
