/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

abstract class DocumentsSynchonizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.DocumentsSynchonizer");
  private Document myOriginal = null;
  private Document myCopy = null;
  private final Project myProject;

  private boolean myDuringModification = false;
  private int myAssignedCount = 0;

  private final DocumentAdapter myOriginalListener = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      if (myDuringModification) return;
      onOriginalChanged(e, getCopy());
    }
  };

  private final DocumentAdapter myCopyListener = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      if (myDuringModification) return;
      onCopyChanged(e, getOriginal());
    }
  };
  private final PropertyChangeListener myROListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      if (Document.PROP_WRITABLE.equals(evt.getPropertyName())) getCopy().setReadOnly(!getOriginal().isWritable());
    }
  };

  protected DocumentsSynchonizer(Project project) {
    myProject = project;
  }

  protected abstract void onCopyChanged(DocumentEvent event, Document original);

  protected abstract void onOriginalChanged(DocumentEvent event, Document copy);

  protected abstract void beforeListenersAttached(Document original, Document copy);

  protected abstract Document createOriginal();

  protected abstract Document createCopy();

  protected void replaceString(final Document document, final int startOffset, final int endOffset, final String newText) {
    LOG.assertTrue(!myDuringModification);
    try {
      myDuringModification = true;
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          LOG.assertTrue(endOffset <= document.getTextLength());
          document.replaceString(startOffset, endOffset, newText);
        }
      }, DiffBundle.message("save.merge.result.command.name"), null);
    }
    finally {
      myDuringModification = false;
    }
  }

  public void listenDocuments(boolean startListen) {
    int prevAssignedCount = myAssignedCount;
    if (startListen) {
      myAssignedCount++;
    }
    else {
      myAssignedCount--;
    }
    LOG.assertTrue(myAssignedCount >= 0);
    if (prevAssignedCount == 0 && myAssignedCount > 0) startListen();
    if (myAssignedCount == 0 && prevAssignedCount > 0) stopListen();
  }

  private void startListen() {
    final Document original = getOriginal();
    final Document copy = getCopy();
    if (original == null || copy == null) return;

    // if we don't ignore copy's events in undo manager, we will receive
    // notification for the same event twice and undo will work incorrectly
    UndoManager.getInstance(myProject).registerDocumentCopy(original, copy);

    beforeListenersAttached(original, copy);
    original.addDocumentListener(myOriginalListener);
    copy.addDocumentListener(myCopyListener);
    original.addPropertyChangeListener(myROListener);
  }


  private void stopListen() {
    if (myOriginal != null) {
      myOriginal.removeDocumentListener(myOriginalListener);
      myOriginal.removePropertyChangeListener(myROListener);
    }

    if (myCopy != null) {
      myCopy.removeDocumentListener(myCopyListener);
    }

    if (myOriginal != null && myCopy != null) {
      UndoManager.getInstance(myProject).unregisterDocumentCopy(myOriginal, myCopy);
    }

    myOriginal = null;
    myCopy = null;
  }

  public Document getOriginal() {
    if (myOriginal == null) myOriginal = createOriginal();
    return myOriginal;
  }

  public Document getCopy() {
    if (myCopy == null) myCopy = createCopy();
    return myCopy;
  }
}
