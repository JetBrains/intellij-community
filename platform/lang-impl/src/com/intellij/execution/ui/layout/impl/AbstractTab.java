package com.intellij.execution.ui.layout.impl;

import javax.swing.*;

abstract class AbstractTab {

  int myIndex;
  String myDisplayName;
  Icon myIcon;

  float myLeftProportion = .2f;
  float myRightProportion = .2f;
  float myBottomProportion = .5f;

  boolean myLeftDetached = false;

  boolean myCenterDetached = false;

  boolean myRightDetached = false;

  boolean myBottomDetached = false;

  void copyFrom(final AbstractTab from) {
    myIndex = from.myIndex;
    myDisplayName = from.myDisplayName;
    myIcon = from.myIcon;

    myLeftProportion = from.myLeftProportion;
    myRightProportion = from.myRightProportion;
    myBottomProportion = from.myBottomProportion;

    myLeftDetached = from.myLeftDetached;
    myCenterDetached = from.myCenterDetached;
    myRightDetached = from.myRightDetached;
    myBottomDetached = from.myBottomDetached;
  }

}