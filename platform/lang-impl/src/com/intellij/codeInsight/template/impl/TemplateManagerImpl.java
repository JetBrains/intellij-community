package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TemplateManagerImpl extends TemplateManager implements ProjectComponent {
  protected Project myProject;
  private boolean myTemplateTesting;
  private final List<Disposable> myDisposables = new ArrayList<Disposable>();

  private static final Key<TemplateState> TEMPLATE_STATE_KEY = Key.create("TEMPLATE_STATE_KEY");

  public TemplateManagerImpl(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
    final EditorFactoryListener myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject) return;
        TemplateState tState = getTemplateState(editor);
        if (tState != null) {
          disposeState(tState);
        }
        editor.putUserData(TEMPLATE_STATE_KEY, null);
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
      }
    });
  }

  public void setTemplateTesting(final boolean templateTesting) {
    myTemplateTesting = templateTesting;
  }

  private void disposeState(final TemplateState tState) {
    tState.dispose();
    myDisposables.remove(tState);
  }

  public Template createTemplate(@NotNull String key, String group) {
    return new TemplateImpl(key, group);
  }

  public Template createTemplate(@NotNull String key, String group, String text) {
    return new TemplateImpl(key, text, group);
  }

  public static TemplateState getTemplateState(Editor editor) {
    return editor.getUserData(TEMPLATE_STATE_KEY);
  }

  void clearTemplateState(final Editor editor) {
    TemplateState prevState = getTemplateState(editor);
    if (prevState != null) {
      disposeState(prevState);
    }
    editor.putUserData(TEMPLATE_STATE_KEY, null);
  }

  private TemplateState initTemplateState(final Editor editor) {
    clearTemplateState(editor);
    TemplateState state = new TemplateState(myProject, editor);
    myDisposables.add(state);
    editor.putUserData(TEMPLATE_STATE_KEY, state);
    return state;
  }

  public boolean startTemplate(@NotNull Editor editor, char shortcutChar) {
    return startTemplate(this, editor, shortcutChar);
  }

  public void startTemplate(@NotNull final Editor editor, @NotNull Template template) {
    startTemplate(editor, template, null);
  }

  public void startTemplate(@NotNull Editor editor, String selectionString, @NotNull Template template) {
    startTemplate(editor, selectionString, template, null);
  }

  private void startTemplate(final Editor editor, final String selectionString, final Template template, TemplateEditingListener listener) {
    final TemplateState templateState = initTemplateState(editor);

    templateState.getProperties().put(ExpressionContext.SELECTION, selectionString);

    if (listener != null) {
      templateState.addTemplateStateListener(listener);
    }
    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        public void run() {
          if (selectionString != null) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                EditorModificationUtil.deleteSelectedText(editor);
              }
            });
          } else {
            editor.getSelectionModel().removeSelection();
          }
          templateState.start((TemplateImpl) template);
        }
      },
      CodeInsightBundle.message("insert.code.template.command"), null
    );

    if (shouldSkipInTests()) {
      if (!templateState.isFinished()) templateState.gotoEnd();
    }
  }

  public boolean shouldSkipInTests() {
    return ApplicationManager.getApplication().isUnitTestMode() && !myTemplateTesting;
  }

  public void startTemplate(@NotNull final Editor editor, @NotNull final Template template, TemplateEditingListener listener) {
    startTemplate(editor, null, template, listener);
  }

  public boolean startTemplate(TemplateManagerImpl templateManager, final Editor editor, char shortcutChar) {
    final Document document = editor.getDocument();
    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
    if (file == null) return false;

    TemplateSettings templateSettings = TemplateSettings.getInstance();
    CharSequence text = document.getCharsSequence();
    final int caretOffset = editor.getCaretModel().getOffset();
    TemplateImpl template = null;
    int wordStart = 0;
    for (int i = templateSettings.getMaxKeyLength(); i >= 1 ; i--) {
      wordStart = caretOffset - i;
      if (wordStart < 0) {
        continue;
      }
      String key = text.subSequence(wordStart, caretOffset).toString();
      template = templateSettings.getTemplate(key);
      if (template != null && template.isDeactivated()) {
        template = null;
      }
      if (template != null) {
        if (Character.isJavaIdentifierStart(key.charAt(0))) {
          if (wordStart > 0 && Character.isJavaIdentifierPart(text.charAt(wordStart - 1))) {
            template = null;
            continue;
          }
        }
        break;
      }
    }

    if (template == null) return false;

    if (shortcutChar != 0 && getShortcutChar(template) != shortcutChar) {
      return false;
    }

    if (template.isSelectionTemplate()) return false;

    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(myProject).commitDocument(document);
        }
      },
      "", null
    );
    if (!isApplicable(file, caretOffset - template.getKey().length(), template)) {
      return false;
    }
    if (!FileDocumentManager.getInstance().fileForDocumentCheckedOutSuccessfully(editor.getDocument(), myProject)) {
        return false;
    }
    final int wordStart0 = wordStart;
    final TemplateImpl template0 = template;
    final TemplateState templateState0 = templateManager.initTemplateState(editor);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      myProject, new Runnable() {
        public void run() {
          editor.getDocument().deleteString(wordStart0, caretOffset);
          editor.getCaretModel().moveToOffset(wordStart0);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
          templateState0.start(template0);
        }
      },
      CodeInsightBundle.message("insert.code.template.command"), null
    );
    return true;
  }

  private static char getShortcutChar(TemplateImpl template) {
    char c = template.getShortcutChar();
    if (c == TemplateSettings.DEFAULT_CHAR) {
      return TemplateSettings.getInstance().getDefaultShortcutChar();
    }
    else {
      return c;
    }
  }

  public TemplateContextType getContextType(@NotNull PsiFile file, int offset) {
    final TemplateContextType[] typeCollection = getAllContextTypes();
    LinkedList<TemplateContextType> userDefinedExtensionsFirst = new LinkedList<TemplateContextType>();
    for(TemplateContextType contextType: typeCollection) {
      if (contextType.getClass().getName().startsWith("com.intellij.codeInsight.template")) userDefinedExtensionsFirst.addLast(contextType);
      else userDefinedExtensionsFirst.addFirst(contextType);
    }
    for(TemplateContextType contextType: userDefinedExtensionsFirst) {
      if (contextType.isInContext(file, offset)) {
        return contextType;
      }
    }
    assert false: "OtherContextType should match any context";
    return null;
  }

  public static TemplateContextType[] getAllContextTypes() {
    return Extensions.getExtensions(TemplateContextType.EP_NAME);
  }

  @NotNull
  public String getComponentName() {
    return "TemplateManager";
  }

  @Nullable
  public Template getActiveTemplate(@NotNull Editor editor) {
    final TemplateState templateState = getTemplateState(editor);
    return templateState != null ? templateState.getTemplate() : null;
  }

  static boolean isApplicable(PsiFile file, int offset, TemplateImpl template) {
    TemplateManager instance = getInstance(file.getProject());
    TemplateContext context = template.getTemplateContext();
    if (context.isEnabled(instance.getContextType(file, offset))) {
      return true;
    }
    Language baseLanguage = file.getViewProvider().getBaseLanguage();
    if (baseLanguage != file.getLanguage()) {
      PsiFile basePsi = file.getViewProvider().getPsi(baseLanguage);
      if (basePsi != null && context.isEnabled(instance.getContextType(basePsi, offset))) {
        return true;
      }
    }

    // if we have, for example, a Ruby fragment in RHTML selected with its exact bounds, the file language and the base
    // language will be ERb, so we won't match HTML templates for it. but they're actually valid
    if (offset > 0) {
      final Language prevLanguage = PsiUtilBase.getLanguageAtOffset(file, offset - 1);
      final PsiFile prevPsi = file.getViewProvider().getPsi(prevLanguage);
      if (prevPsi != null && context.isEnabled(instance.getContextType(prevPsi, offset - 1))) {
        return true;
      }
    }

    return false;
  }
}
