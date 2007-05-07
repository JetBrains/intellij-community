/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CommittableUtil {

  public void commit(Committable committable) {
    committable.commit();
  }

  public void queueReset(Committable committable) {
    committable.reset();
  }

  public static void updateHighlighting(@Nullable Committable committable) {
    if (committable instanceof Highlightable) {
      ((Highlightable)committable).updateHighlighting();
    }
  }
}
