/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;



/**
 * author: lesya
 */
public interface LvcsAction {
  LvcsAction EMPTY = new LvcsAction(){
    public void finish() {
    }

    public String getName() {
      return "";
    }
  };

  void finish();

  String getName();
}
