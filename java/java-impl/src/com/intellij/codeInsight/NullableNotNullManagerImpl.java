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
package com.intellij.codeInsight;

import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerNotNullableSerializer;

import java.util.Collections;
import java.util.List;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element> {

  public NullableNotNullManagerImpl() {
    myNotNulls.addAll(getPredefinedNotNulls());
  }

  @Override
  public List<String> getPredefinedNotNulls() {
    return JpsJavaCompilerNotNullableSerializer.DEFAULT_NOT_NULLS;
  }

  @Override
  protected boolean hasHardcodedContracts(PsiElement element) {
    return HardcodedContracts.hasHardcodedContracts(element);
  }


  @SuppressWarnings("deprecation")
  @Override
  public Element getState() {
    final Element component = new Element("component");

    if (hasDefaultValues()) {
      return component;
    }

    try {
      DefaultJDOMExternalizer.writeExternal(this, component);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return component;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      if (myNullables.isEmpty()) {
        Collections.addAll(myNullables, DEFAULT_NULLABLES);
      }
      if (myNotNulls.isEmpty()) {
        myNotNulls.addAll(getPredefinedNotNulls());
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }
}
