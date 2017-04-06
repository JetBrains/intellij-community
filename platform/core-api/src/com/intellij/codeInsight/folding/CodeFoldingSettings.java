/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.BooleanTrackableProperty;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;

@SuppressWarnings({"deprecation", "DeprecatedIsStillUsed"})
public class CodeFoldingSettings {
  /** @deprecated Use corresponding getters/setters or property */
  @Transient
  public boolean COLLAPSE_IMPORTS = true;
  
  /** @deprecated Use corresponding getters/setters or property */
  @Transient
  public boolean COLLAPSE_METHODS;
  
  /** @deprecated Use corresponding getters/setters or property */
  @Transient
  public boolean COLLAPSE_FILE_HEADER = true;
  
  /** @deprecated Use corresponding getters/setters or property */
  @Transient
  public boolean COLLAPSE_DOC_COMMENTS;
  
  /** @deprecated Use corresponding getters/setters or property */
  @Transient
  public boolean COLLAPSE_CUSTOM_FOLDING_REGIONS;

  private BooleanTrackableProperty myCollapseImports = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty myCollapseMethods = new BooleanTrackableProperty();
  private BooleanTrackableProperty myCollapseFileHeader = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty myCollapseDocComments = new BooleanTrackableProperty();
  private BooleanTrackableProperty myCollapseCustomFoldingRegions = new BooleanTrackableProperty();
  

  public static CodeFoldingSettings getInstance() {
    return ServiceManager.getService(CodeFoldingSettings.class);
  }
  
  @OptionTag("COLLAPSE_IMPORTS")
  public boolean isCollapseImports() {
    return COLLAPSE_IMPORTS;
  }

  public void setCollapseImports(boolean value) {
    COLLAPSE_IMPORTS = value;
    myCollapseImports.setValue(value);
  }
  
  public BooleanTrackableProperty getCollapseImportsProperty() {
    return myCollapseImports;
  }
  
  @OptionTag("COLLAPSE_METHODS")
  public boolean isCollapseMethods() {
    return COLLAPSE_METHODS;
  }

  public void setCollapseMethods(boolean value) {
    COLLAPSE_METHODS = value;
    myCollapseMethods.setValue(value);
  }
  
  public BooleanTrackableProperty getCollapseMethodsProperty() {
    return myCollapseMethods;
  }
  
  @OptionTag("COLLAPSE_FILE_HEADER")
  public boolean isCollapseFileHeader() {
    return COLLAPSE_FILE_HEADER;
  }

  public void setCollapseFileHeader(boolean value) {
    COLLAPSE_FILE_HEADER = value;
    myCollapseFileHeader.setValue(value);
  }
  
  public BooleanTrackableProperty getCollapseFileHeaderProperty() {
    return myCollapseFileHeader;
  }
  
  @OptionTag("COLLAPSE_DOC_COMMENTS")
  public boolean isCollapseDocComments() {
    return COLLAPSE_DOC_COMMENTS;
  }

  public void setCollapseDocComments(boolean value) {
    COLLAPSE_DOC_COMMENTS = value;
    myCollapseDocComments.setValue(value);
  }
  
  public BooleanTrackableProperty getCollapseDocCommentsProperty() {
    return myCollapseDocComments;
  }
  
  @OptionTag("COLLAPSE_CUSTOM_FOLDING_REGIONS")
  public boolean isCollapseCustomFoldingRegions() {
    return COLLAPSE_CUSTOM_FOLDING_REGIONS;
  }

  public void setCollapseCustomFoldingRegions(boolean value) {
    COLLAPSE_CUSTOM_FOLDING_REGIONS = value;
    myCollapseCustomFoldingRegions.setValue(value);
  }
  
  public BooleanTrackableProperty getCollapseCustomFoldingRegionsProperty() {
    return myCollapseCustomFoldingRegions;
  }
}
