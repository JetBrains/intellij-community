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

package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public interface XmlAttributeDescriptor extends PsiMetaData{
  XmlAttributeDescriptor[] EMPTY = new XmlAttributeDescriptor[0];

  boolean isRequired();
  boolean isFixed();
  boolean hasIdType();
  boolean hasIdRefType();

  @Nullable
  String getDefaultValue();

  //todo: refactor to hierarchy of value descriptor?
  boolean isEnumerated();
  String[] getEnumeratedValues();

  @Nullable
  String validateValue(XmlElement context, String value);
}
