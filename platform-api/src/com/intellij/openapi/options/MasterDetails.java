package com.intellij.openapi.options;

import com.intellij.openapi.ui.DetailsComponent;

import javax.swing.*;

public interface MasterDetails {

  void initUi();

  JComponent getToolbar();
  JComponent getMaster();
  DetailsComponent getDetails();

}