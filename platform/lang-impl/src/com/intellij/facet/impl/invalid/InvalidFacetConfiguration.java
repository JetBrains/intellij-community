/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.impl.invalid;

import com.intellij.facet.FacetConfiguration;
import org.jetbrains.jps.model.serialization.facet.FacetState;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author nik
 */
public class InvalidFacetConfiguration implements FacetConfiguration {
  private final FacetState myFacetState;
  private final String myErrorMessage;

  public InvalidFacetConfiguration(FacetState facetState, String errorMessage) {
    myFacetState = facetState;
    myErrorMessage = errorMessage;
  }

  public FacetState getFacetState() {
    return myFacetState;
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
      new InvalidFacetEditor(editorContext, myErrorMessage)
    };
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  public String getErrorMessage() {
    return myErrorMessage;
  }
}
