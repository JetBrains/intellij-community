/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.libraries.LibrariesValidationComponent;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.util.EventDispatcher;

import javax.swing.*;

/**
 * @author nik
 */
public class LibrariesValidationComponentImpl implements LibrariesValidationComponent {
  private final EventDispatcher<ValidityListener> myDispatcher = EventDispatcher.create(ValidityListener.class);
  private final FacetErrorPanel myErrorPanel;
  private final FacetLibrariesValidatorImpl myLibrariesValidator;
  private final Module myModule;

  public LibrariesValidationComponentImpl(LibraryInfo[] requiredLibraries, final Module module, String defaultLibraryName) {
    myErrorPanel = new FacetErrorPanel();
    FacetLibrariesValidatorDescription description = new FacetLibrariesValidatorDescription(defaultLibraryName);
    myModule = module;
    myLibrariesValidator = new FacetLibrariesValidatorImpl(requiredLibraries, description, new LibrariesValidatorContextImpl(myModule),
                                                           myErrorPanel.getValidatorsManager());
    myErrorPanel.getValidatorsManager().registerValidator(myLibrariesValidator);
    myErrorPanel.addListener(new Runnable() {
      public void run() {
        myDispatcher.getMulticaster().valididyChanged(myErrorPanel.isOk());
      }
    });
  }

  public JComponent getComponent() {
    return myErrorPanel.getComponent();
  }

  public void validate() {
    myErrorPanel.getValidatorsManager().validate();
  }

  public boolean isValid() {
    return myErrorPanel.isOk();
  }

  public void addValidityListener(final ValidityListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeValidityListener(final ValidityListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setupLibraries() {
  }
}
