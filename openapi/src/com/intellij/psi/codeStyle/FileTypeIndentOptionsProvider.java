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
 * User: Maxim.Mossienko
 * Date: Nov 28, 2006
 * Time: 4:33:11 PM
 */
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.fileTypes.FileType;

public interface FileTypeIndentOptionsProvider {
  CodeStyleSettings.IndentOptions createIndentOptions();

  FileType getFileType();
}