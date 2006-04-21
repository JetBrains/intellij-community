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
package com.intellij.psi.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public interface XmlFile extends PsiFile, XmlElement {
  Key<Map<String,String>> ANT_FILE_PROPERTIES = Key.create("ANT_FILE_PROPERTIES");
  Key ANT_BUILD_FILE = Key.create("ANT_BUILD_FILE");

  @Nullable
  XmlDocument getDocument();
}
