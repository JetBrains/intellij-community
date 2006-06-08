/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

import javax.swing.*;
import java.util.Set;
import java.util.HashSet;

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
      if (myShowing && !isCommitting()) {
        myDirty = true;
      }
    }
  };

  protected boolean isCommitting() {
    return CommittableUtil.isCommitting();
  }


  public UndoHelper(final Project project, final Committable committable) {
    myProject = project;

    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
      public void commandStarted(CommandEvent event) {
        undoTransparentActionStarted();
      }

      public void undoTransparentActionStarted() {
        myDirty = false;
      }

      public void undoTransparentActionFinished() {
        if (myDirty) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              committable.reset();
            }
          });
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
