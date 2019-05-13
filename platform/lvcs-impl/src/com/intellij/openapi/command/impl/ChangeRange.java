// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.command.impl;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.changes.Change;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.UndoChangeRevertingVisitor;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ChangeRange {
  private final IdeaGateway myGateway;
  private final LocalHistoryFacade myVcs;
  private final Long myFromChangeId;
  @Nullable private final Long myToChangeId;

  public ChangeRange(IdeaGateway gw, LocalHistoryFacade vcs, @NotNull Long changeId) {
    this(gw, vcs, changeId, changeId);
  }

  private ChangeRange(IdeaGateway gw, LocalHistoryFacade vcs, @Nullable Long fromChangeId, @Nullable Long toChangeId) {
    myGateway = gw;
    myVcs = vcs;
    myFromChangeId = fromChangeId;
    myToChangeId = toChangeId;
  }

  public ChangeRange revert(ChangeRange reverse) throws IOException {
    final Ref<Long> first = new Ref<>();
    final Ref<Long> last = new Ref<>();
    LocalHistoryFacade.Listener l = new LocalHistoryFacade.Listener() {
      @Override
      public void changeAdded(Change c) {
        if (first.isNull()) first.set(c.getId());
        last.set(c.getId());
      }
    };
    myVcs.addListener(l, null);
    try {
      myVcs.accept(new UndoChangeRevertingVisitor(myGateway, myToChangeId, myFromChangeId));
    }
    catch (UndoChangeRevertingVisitor.RuntimeIOException e) {
      throw (IOException)e.getCause();
    }
    finally {
      myVcs.removeListener(l);
    }

    if (reverse != null) {
      if (first.isNull()) first.set(reverse.myFromChangeId);
      if (last.isNull()) last.set(reverse.myToChangeId);
    }
    return new ChangeRange(myGateway, myVcs, first.get(), last.get());
  }
}
