/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class CustomCodeStyleSettings implements Cloneable {
  private final CodeStyleSettings myContainer;
  private final String myTagName;

  protected CustomCodeStyleSettings(@NonNls @NotNull String tagName, CodeStyleSettings container) {
    myTagName = tagName;
    myContainer = container;
  }

  public final CodeStyleSettings getContainer() {
    return myContainer;
  }

  @NonNls @NotNull
  public final String getTagName() {
    return myTagName;
  }

  /**
   * in case settings save additional top-level tags, list the list of them to prevent serializer to treat such tag as unknown settings.
   */
  @NotNull
  public List<String> getKnownTagNames() {
    return Collections.singletonList(getTagName());
  }

  public void readExternal(Element parentElement) throws InvalidDataException {
    Element child = parentElement.getChild(myTagName);
    if (child != null) {
      DefaultJDOMExternalizer.readExternal(this, child);
    }
  }

  public void writeExternal(Element parentElement, @NotNull final CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    final Element childElement = new Element(myTagName);
    DefaultJDOMExternalizer.writeExternal(this, childElement, new DifferenceFilter<>(this, parentSettings));
    if (!childElement.getContent().isEmpty()) {
      parentElement.addContent(childElement);
    }
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * For compatibility with old code style settings stored in CodeStyleSettings.
   */
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CustomCodeStyleSettings)) return false;
    if (!ReflectionUtil.comparePublicNonFinalFields(this, obj)) return false;
    return true;
  }
}
