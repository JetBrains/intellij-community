// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data;

import java.util.List;

public class OptionsPanelInfo {

  public String type;
  public String text;

  public List<OptionsPanelInfo> children = null;

  public OptionsPanelInfo() {
  }

  public OptionsPanelInfo(String type, String text) {
    this.type = type;
    this.text = text;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public List<OptionsPanelInfo> getChildren() {
    return children;
  }

  public void setChildren(List<OptionsPanelInfo> children) {
    this.children = children;
  }
}
