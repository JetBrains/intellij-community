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



/**
 * @deprecated use LocalHistory instead
 */
public interface LvcsLabel extends Comparable<LvcsLabel>{
  byte TYPE_BEFORE_ACTION = 1;
  byte TYPE_AFTER_ACTION = 2;

  byte TYPE_OTHER = 3;
  byte TYPE_TESTS_SUCCESSFUL = 4;
  byte TYPE_TESTS_FAILED = 5;

  byte TYPE_USER = 6;

  int getType();
  String getName();
  String getPath();
  long getDate();
  String getAction();
  int getVersionId();

  LvcsLabel getRecentChangesBeforeLabel( long date );

  int compareTo(LvcsRevision revision);
}
