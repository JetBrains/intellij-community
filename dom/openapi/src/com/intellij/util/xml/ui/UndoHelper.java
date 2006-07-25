/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class UndoHelper {
  private final Project myProject;
  private boolean myShowing;
  private final Set<Document> myCurrentDocuments = new HashSet<Document>();
  private boolean myDirty;
  private final DocumentAdapter myDocumentAdapter = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      if (myShowing) {
        myDirty = true;
      }
    }
  };

  public UndoHelper(final Project project, final Committable committable) {
    myProject = project;
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final CommittableUtil committableUtil = project.getComponent(CommittableUtil.class);
    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
      public void commandStarted(CommandEvent event) {
        undoTransparentActionStarted();
      }

      public void undoTransparentActionStarted() {
        myDirty = false;
      }

      public void undoTransparentActionFinished() {
        if (myDirty) {
          psiDocumentManager.commitAllDocuments();
          committableUtil.queueReset(committable);
        }
      }

      public void commandFinished(CommandEvent event) {
        undoTransparentActionFinished();
      }
    }, committable);
  }

  public final void startListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.addDocumentListener(myDocumentAdapter);
    }
  }

  public final void stopListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.removeDocumentListener(myDocumentAdapter);
    }
  }

  public final void setShowing(final boolean showing) {
    commitAllDocuments();
    myShowing = showing;
  }

  public final void commitAllDocuments() {
    final PsiDocumentManager manager = getDocumentManager();
    for (final Document document : myCurrentDocuments) {
      manager.commitDocument(document);
    }
  }

  private PsiDocumentManager getDocumentManager() {
    return PsiDocumentManager.getInstance(myProject);
  }

  public final void addWatchedDocument(final Document document) {
    stopListeningDocuments();
    myCurrentDocuments.add(document);
    startListeningDocuments();
  }

  public final void removeWatchedDocument(final Document document) {
    stopListeningDocuments();
    myCurrentDocuments.remove(document);
    startListeningDocuments();
  }

  public final Document[] getDocuments() {
    return myCurrentDocuments.toArray(new Document[myCurrentDocuments.size()]);
  }


}
