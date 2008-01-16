/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.10.2006
 * Time: 17:00:37
 */
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;

@SuppressWarnings({"deprecation"})
public final class DataKeys extends LangDataKeys {
  private DataKeys() {
  }

  public static final DataKey<ExporterToTextFile> EXPORTER_TO_TEXT_FILE = DataKey.create(DataConstants.EXPORTER_TO_TEXT_FILE);

  @Deprecated
  public static final DataKey<ChangeList[]> CHANGE_LISTS = DataKey.create(DataConstants.CHANGE_LISTS);
  @Deprecated
  public static final DataKey<Change[]> CHANGES = DataKey.create(DataConstants.CHANGES);

}

