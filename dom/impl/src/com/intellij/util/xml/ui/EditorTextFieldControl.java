/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author peter
 */
public abstract class EditorTextFieldControl<T extends JComponent> extends BaseControl<T, String> {
  private static final JTextField J_TEXT_FIELD = new JTextField() {
    public void addNotify() {
      throw new UnsupportedOperationException("Shouldn't be shown");
    }

    public void setVisible(boolean aFlag) {
      throw new UnsupportedOperationException("Shouldn't be shown");
    }
  };
  private final boolean myCommitOnEveryChange;
  private final DocumentListener myListener = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      commit();
    }
  };

  protected EditorTextFieldControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper);
    myCommitOnEveryChange = commitOnEveryChange;
  }


  protected EditorTextFieldControl(final DomWrapper<String> domWrapper) {
    this(domWrapper, false);
  }

  protected abstract EditorTextField getEditorTextField(T component);

  public final EditorTextField getEditorTextField() {
    return getEditorTextField(getComponent());
  }

  protected void doReset() {
    if (myCommitOnEveryChange) {
      final EditorTextField textField = getEditorTextField(getComponent());
      textField.getDocument().removeDocumentListener(myListener);
      super.doReset();
      textField.getDocument().addDocumentListener(myListener);
    } else {
      super.doReset();
    }
  }

  protected JComponent getComponentToListenFocusLost(final T component) {
    return getEditorTextField(getComponent());
  }

  protected JComponent getHighlightedComponent(final T component) {
    return J_TEXT_FIELD;
  }

  protected T createMainComponent(T boundedComponent) {
    final Project project = getProject();
    boundedComponent = createMainComponent(boundedComponent, project);

    final EditorTextField editorTextField = getEditorTextField(boundedComponent);
    editorTextField.setSupplementary(true);
    final PsiCodeFragment file = (PsiCodeFragment)PsiDocumentManager.getInstance(project).getPsiFile(editorTextField.getDocument());
    EditorTextFieldControlHighlighter.getEditorTextFieldControlHighlighter(project).addFile(file, new Factory<DomElement>() {
      public DomElement create() {
        return getDomElement();
      }
    });

    if (myCommitOnEveryChange) {
      editorTextField.getDocument().addDocumentListener(myListener);
    }
    return boundedComponent;
  }

  protected abstract T createMainComponent(T boundedComponent, Project project);

  protected String getValue() {
    return getEditorTextField(getComponent()).getText();
  }

  protected void setValue(final String value) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        final T component = EditorTextFieldControl.this.getComponent();
        getEditorTextField(component).getDocument().replaceString(0, getValue().length(), value == null ? "" : value);
      }
    }.execute();
  }

  protected void updateComponent() {
    final DomElement domElement = getDomElement();
    if (domElement == null || !domElement.isValid()) return;

    final EditorTextField textField = getEditorTextField(getComponent());
    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final DomElement domElement = getDomElement();
        if (!project.isOpen() || domElement == null || !domElement.isValid()) return;

        final DomElementAnnotationsManager manager = DomElementAnnotationsManager.getInstance(project);
        final List<DomElementProblemDescriptor> errorProblems = manager.getProblems(domElement, true);
        final List<DomElementProblemDescriptor> warningProblems = manager.getProblems(domElement, true, true, HighlightSeverity.WARNING);

        Color background = getDefaultBackground();
        if (errorProblems.size() > 0 && textField.getText().trim().length() == 0) {
          background = getErrorBackground();
        }
        else if (warningProblems.size() > 0) {
          background = getWarningBackground();
        }
        textField.setBackground(background);

        final Editor editor = textField.getEditor();
        if (editor != null && isCommitted()) {
          DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
        }
      }
    });

  }

  public boolean canNavigate(final DomElement element) {
    return getDomElement().equals(element);
  }

  public void navigate(final DomElement element) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getEditorTextField(EditorTextFieldControl.this.getComponent()).requestFocus();
        getEditorTextField(EditorTextFieldControl.this.getComponent()).selectAll();
      }
    });
  }
}
