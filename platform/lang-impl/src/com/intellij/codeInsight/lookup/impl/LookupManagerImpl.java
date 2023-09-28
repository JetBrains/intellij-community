// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.BitUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class LookupManagerImpl extends LookupManager {
  private static final Logger LOG = Logger.getInstance(LookupManagerImpl.class);
  private final Project myProject;
  private LookupImpl myActiveLookup = null;
  private Editor myActiveLookupEditor = null;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  public static final Key<Boolean> SUPPRESS_AUTOPOPUP_JAVADOC = Key.create("LookupManagerImpl.suppressAutopopupJavadoc");

  public LookupManagerImpl(@NotNull Project project) {
    myProject = project;

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        if (project == myProject) {
          Lookup lookup = getActiveLookup();
          if (lookup != null && BitUtil.isSet(flags, HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE)) {
            lookup.addLookupListener(new LookupListener() {
              @Override
              public void currentItemChanged(@NotNull LookupEvent event) {
                hint.hide();
              }

              @Override
              public void itemSelected(@NotNull LookupEvent event) {
                hint.hide();
              }

              @Override
              public void lookupCanceled(@NotNull LookupEvent event) {
                hint.hide();
              }
            });
          }
        }
      }
    });

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        hideActiveLookup();
      }

      @Override
      public void exitDumbMode() {
        hideActiveLookup();
      }
    });


    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor) {
          hideActiveLookup();
        }
      }
    }, myProject);
  }

  @Override
  @Nullable
  public LookupEx showLookup(@NotNull final Editor editor,
                           LookupElement @NotNull [] items,
                           @NotNull final String prefix,
                           @NotNull final LookupArranger arranger) {
    for (LookupElement item : items) {
      assert item != null;
    }

    LookupImpl lookup = createLookup(editor, items, prefix, arranger);
    return lookup.showLookup() ? lookup : null;
  }

  @NotNull
  @Override
  public LookupImpl createLookup(@NotNull final Editor editor,
                                 LookupElement @NotNull [] items,
                                 @NotNull final String prefix,
                                 @NotNull final LookupArranger arranger) {
    hideActiveLookup();

    final LookupImpl lookup = createLookup(editor, arranger, myProject);

    ThreadingAssertions.assertEventDispatchThread();

    myActiveLookup = lookup;
    myActiveLookupEditor = editor;
    Disposer.register(lookup, new Disposable() {
      @Override
      public void dispose() {
        myActiveLookup = null;
        myActiveLookupEditor = null;
        fireActiveLookupChanged(lookup, null);
      }
    });

    if (items.length > 0) {
      CamelHumpMatcher matcher = new CamelHumpMatcher(prefix);
      for (LookupElement item : items) {
        myActiveLookup.addItem(item, matcher);
      }
      myActiveLookup.refreshUi(true, true);
    }

    fireActiveLookupChanged(null, myActiveLookup);
    return lookup;
  }

  void fireActiveLookupChanged(LookupImpl oldLookup, LookupImpl newLookup) {
    myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, oldLookup, newLookup);
    myProject.getMessageBus().syncPublisher(LookupManagerListener.TOPIC).activeLookupChanged(oldLookup, newLookup);
  }

  public static boolean isAutoPopupJavadocSupportedBy(@NotNull LookupElement lookupElement) {
    return lookupElement.getUserData(SUPPRESS_AUTOPOPUP_JAVADOC) == null;
  }

  @NotNull
  protected LookupImpl createLookup(@NotNull Editor editor, @NotNull LookupArranger arranger, Project project) {
    return new LookupImpl(project, editor, arranger);
  }

  @Override
  public void hideActiveLookup() {
    LookupImpl lookup = myActiveLookup;
    if (lookup != null) {
      lookup.checkValid();
      lookup.hide();
      LOG.assertTrue(lookup.isLookupDisposed(), "Should be disposed");
    }
  }

  @Override
  public LookupEx getActiveLookup() {
    if (myActiveLookup != null && myActiveLookup.isLookupDisposed()) {
      LookupImpl lookup = myActiveLookup;
      myActiveLookup = null;
      lookup.checkValid();
    }

    return myActiveLookup;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener, @NotNull Disposable disposable) {
    addPropertyChangeListener(listener);
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        removePropertyChangeListener(listener);
      }
    });
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }


  @TestOnly
  public void forceSelection(char completion, int index){
    if(myActiveLookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupElement lookupItem = myActiveLookup.getItems().get(index);
    myActiveLookup.setCurrentItem(lookupItem);
    myActiveLookup.finishLookup(completion);
  }

  @TestOnly
  public void forceSelection(char completion, LookupElement item){
    myActiveLookup.setCurrentItem(item);
    myActiveLookup.finishLookup(completion);
  }

  @TestOnly
  public void clearLookup() {
    if (myActiveLookup != null) {
      myActiveLookup.hide();
      myActiveLookup = null;
    }
  }
}
