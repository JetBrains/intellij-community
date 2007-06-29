/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.10.2006
 * Time: 18:13:58
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public interface VcsDataKeys {
  DataKey<File[]> IO_FILE_ARRAY = DataKey.create(VcsDataConstants.IO_FILE_ARRAY);
  DataKey<File> IO_FILE = DataKey.create(VcsDataConstants.IO_FILE);
  DataKey<VcsFileRevision> VCS_FILE_REVISION = DataKey.create(VcsDataConstants.VCS_FILE_REVISION);
  DataKey<VirtualFile> VCS_VIRTUAL_FILE = DataKey.create(VcsDataConstants.VCS_VIRTUAL_FILE);
  DataKey<FilePath> FILE_PATH = DataKey.create(VcsDataConstants.FILE_PATH);
  DataKey<FilePath[]> FILE_PATH_ARRAY = DataKey.create(VcsDataConstants.FILE_PATH_ARRAY);
}