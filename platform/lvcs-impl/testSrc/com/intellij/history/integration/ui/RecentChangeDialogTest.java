// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui;

import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.RecentChange;
import com.intellij.history.integration.ui.models.RecentChangeDialogModel;
import com.intellij.history.integration.ui.models.RecentChangeKt;
import com.intellij.history.integration.ui.views.RecentChangeDialog;
import com.intellij.openapi.util.Disposer;

public class RecentChangeDialogTest extends LocalHistoryUITestCase {
  public void testDialogWork() {
    getVcs().beginChangeSet();
    createChildData(myRoot, "f.txt");
    getVcs().endChangeSet("change");

    RecentChange c = RecentChangeKt.getRecentChanges(getVcs(), getRootEntry()).get(0);
    RecentChangeDialog d = null;

    try {
      d = new RecentChangeDialog(myProject, myGateway, c);
    }
    finally {
      if (d != null) {
        Disposer.dispose(d);
      }
    }
  }

  public void testRevertChange() throws Exception {
    getVcs().beginChangeSet();
    createChildData(myRoot, "f1.txt");
    getVcs().endChangeSet("change");

    getVcs().beginChangeSet();
    createChildData(myRoot, "f2.txt");
    getVcs().endChangeSet("another change");

    RecentChange c = RecentChangeKt.getRecentChanges(getVcs(), getRootEntry()).get(1);
    RecentChangeDialogModel m = new RecentChangeDialogModel(myProject, myGateway, getVcs(), c);

    Reverter r = m.createReverter();
    r.revert();

    assertNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
  }
}