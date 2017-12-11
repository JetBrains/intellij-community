/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.actions;


public class GotoNextMarkedOccurrenceAction extends GotoMarkedOccurrenceBase {
  public GotoNextMarkedOccurrenceAction() {
    super((a, b) -> a - b);
  }
}
