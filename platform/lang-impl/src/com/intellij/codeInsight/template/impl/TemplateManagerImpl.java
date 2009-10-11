/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    return startTemplate(editor, shortcutChar, null);
  }

  public void startTemplate(@NotNull final Editor editor, @NotNull Template template) {
    startTemplate(editor, template, null);
  }

  public void startTemplate(@NotNull Editor editor, String selectionString, @NotNull Template template) {
    startTemplate(editor, selectionString, template, null, null);
  }

  public void startTemplate(@NotNull Editor editor, @NotNull Template template, TemplateEditingListener listener,
                            final PairProcessor<String, String> processor) {
    startTemplate(editor, null, template, listener, processor);
  }

  private void startTemplate(final Editor editor, final String selectionString, final Template template, TemplateEditingListener listener,
                             final PairProcessor<String, String> processor) {
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
          templateState.start((TemplateImpl) template, processor);
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
    startTemplate(editor, null, template, listener, null);
  }

  public boolean startTemplate(final Editor editor, char shortcutChar, final PairProcessor<String, String> processor) {
    final Document document = editor.getDocument();
    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
    if (file == null) return false;

    TemplateSettings templateSettings = TemplateSettings.getInstance();
    CharSequence text = document.getCharsSequence();
    final int caretOffset = editor.getCaretModel().getOffset();
    String key = null;
    List<TemplateImpl> candidates = Collections.emptyList();
    for (int i = templateSettings.getMaxKeyLength(); i >= 1 ; i--) {
      int wordStart = caretOffset - i;
      if (wordStart < 0) {
        continue;
      }
      key = text.subSequence(wordStart, caretOffset).toString();
      if (Character.isJavaIdentifierStart(key.charAt(0))) {
        if (wordStart > 0 && Character.isJavaIdentifierPart(text.charAt(wordStart - 1))) {
          continue;
        }
      }

      candidates = templateSettings.collectMatchingCandidates(key, shortcutChar);
      if (!candidates.isEmpty()) break;
    }

    if (candidates.isEmpty()) return false;

    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(myProject).commitDocument(document);
        }
      },
      "", null
    );

    candidates = filterApplicableCandidates(file, caretOffset - key.length(), candidates);
    if (candidates.isEmpty()) {
      return false;
    }
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), myProject)) {
      return false;
    }

    if (candidates.size() == 1) {
      TemplateImpl template = candidates.get(0);
      startTemplateWithPrefix(editor, template, processor);
    }
    else {
      ListTemplatesHandler.showTemplatesLookup(myProject, editor, key, candidates);
    }
    
    return true;
  }

  public void startTemplateWithPrefix(final Editor editor, final TemplateImpl template, @Nullable final PairProcessor<String, String> processor) {
    final int caretOffset = editor.getCaretModel().getOffset();
    final int wordStart = caretOffset - template.getKey().length();
    final TemplateState templateState = initTemplateState(editor);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      myProject, new Runnable() {
        public void run() {
          editor.getDocument().deleteString(wordStart, caretOffset);
          editor.getCaretModel().moveToOffset(wordStart);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
          templateState.start(template, processor);
        }
      },
      CodeInsightBundle.message("insert.code.template.command"), null
    );
  }

  private static List<TemplateImpl> filterApplicableCandidates(PsiFile file, int offset, List<TemplateImpl> candidates) {
    List<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateImpl candidate : candidates) {
      if (isApplicable(file, offset, candidate)) {
        result.add(candidate);
      }
    }
    return result;
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
