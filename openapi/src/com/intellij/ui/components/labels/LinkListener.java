/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.labels;



public interface LinkListener {

  LinkListener NULL = new LinkListener() {
    public void linkSelected(LinkLabel aSource, Object aLinkData) {
    }
  };

  void linkSelected(LinkLabel aSource, Object aLinkData);

}
