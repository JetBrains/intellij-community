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
import com.intellij.util.containers.HashMap;
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

  public void initComponent() {
  }

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
    startTemplate(editor, selectionString, template, true, null, null, null);
  }

  public void startTemplate(@NotNull Editor editor,
                            @NotNull Template template,
                            TemplateEditingListener listener,
                            final PairProcessor<String, String> processor) {
    startTemplate(editor, null, template, true, listener, processor, null);
  }

  private void startTemplate(final Editor editor,
                             final String selectionString,
                             final Template template,
                             boolean inSeparateCommand,
                             TemplateEditingListener listener,
                             final PairProcessor<String, String> processor,
                             final Map<String, String> predefinedVarValues) {
    final TemplateState templateState = initTemplateState(editor);

    templateState.getProperties().put(ExpressionContext.SELECTION, selectionString);

    if (listener != null) {
      templateState.addTemplateStateListener(listener);
    }
    Runnable r = new Runnable() {
      public void run() {
        if (selectionString != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              EditorModificationUtil.deleteSelectedText(editor);
            }
          });
        }
        else {
          editor.getSelectionModel().removeSelection();
        }
        templateState.start((TemplateImpl)template, processor, predefinedVarValues);
      }
    };
    if (inSeparateCommand) {
      CommandProcessor.getInstance().executeCommand(myProject, r, CodeInsightBundle.message("insert.code.template.command"), null);
    }
    else {
      r.run();
    }

    if (shouldSkipInTests()) {
      if (!templateState.isFinished()) templateState.gotoEnd();
    }
  }

  public boolean shouldSkipInTests() {
    return ApplicationManager.getApplication().isUnitTestMode() && !myTemplateTesting;
  }

  public void startTemplate(@NotNull final Editor editor, @NotNull final Template template, TemplateEditingListener listener) {
    startTemplate(editor, null, template, true, listener, null, null);
  }

  public void startTemplate(@NotNull final Editor editor,
                            @NotNull final Template template,
                            boolean inSeparateCommand,
                            Map<String, String> predefinedVarValues,
                            TemplateEditingListener listener) {
    startTemplate(editor, null, template, inSeparateCommand, listener, null, predefinedVarValues);
  }

  private static int passArgumentBack(CharSequence text, int caretOffset) {
    int i = caretOffset - 1;
    for (; i >= 0; i--) {
      char c = text.charAt(i);
      if (isDelimiter(c)) {
        break;
      }
    }
    return i + 1;
  }

  private static boolean isDelimiter(char c) {
    return !Character.isJavaIdentifierPart(c);
  }

  private static <T, U> void addToMap(@NotNull Map<T, U> map, @NotNull Collection<? extends T> keys, U value) {
    for (T key : keys) {
      map.put(key, value);
    }
  }

  private static boolean containsTemplateStartingBefore(Map<TemplateImpl, String> template2argument,
                                                        int offset,
                                                        int caretOffset,
                                                        CharSequence text) {
    for (TemplateImpl template : template2argument.keySet()) {
      String argument = template2argument.get(template);
      int templateStart = getTemplateStart(template, argument, caretOffset, text);
      if (templateStart < offset) {
        return true;
      }
    }
    return false;
  }

  public boolean startTemplate(final Editor editor, char shortcutChar, final PairProcessor<String, String> processor) {
    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
    if (file == null) return false;
    TemplateSettings templateSettings = TemplateSettings.getInstance();

    Map<TemplateImpl, String> template2argument = findMatchingTemplates(file, editor, shortcutChar, templateSettings);

    for (final CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if (shortcutChar == customLiveTemplate.getShortcut()) {
        int caretOffset = editor.getCaretModel().getOffset();
        if (customLiveTemplate.isApplicable(file, caretOffset)) {
          final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
          String key = customLiveTemplate.computeTemplateKey(callback);
          if (key != null) {
            int offsetBeforeKey = caretOffset - key.length();
            CharSequence text = editor.getDocument().getCharsSequence();
            if (template2argument == null || !containsTemplateStartingBefore(template2argument, offsetBeforeKey, caretOffset, text)) {
              customLiveTemplate.expand(key, callback);
              return true;
            }
          }
        }
      }
    }
    return startNonCustomTemplates(template2argument, editor, processor);
  }

  private static int getArgumentOffset(int caretOffset, String argument, CharSequence text) {
    int argumentOffset = caretOffset - argument.length();
    if (argumentOffset > 0 && text.charAt(argumentOffset - 1) == ' ') {
      if (argumentOffset - 2 >= 0 && Character.isJavaIdentifierPart(text.charAt(argumentOffset - 2))) {
        argumentOffset--;
      }
    }
    return argumentOffset;
  }

  private static int getTemplateStart(TemplateImpl template, String argument, int caretOffset, CharSequence text) {
    int templateStart;
    if (argument == null) {
      templateStart = caretOffset - template.getKey().length();
    }
    else {
      int argOffset = getArgumentOffset(caretOffset, argument, text);
      templateStart = argOffset - template.getKey().length();
    }
    return templateStart;
  }

  public Map<TemplateImpl, String> findMatchingTemplates(final PsiFile file,
                                                          Editor editor,
                                                          Character shortcutChar,
                                                          TemplateSettings templateSettings) {
    final Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    final int caretOffset = editor.getCaretModel().getOffset();

    List<TemplateImpl> candidatesWithoutArgument = findMatchingTemplates(text, caretOffset, shortcutChar, templateSettings, false);

    int argumentOffset = passArgumentBack(text, caretOffset);
    String argument = null;
    if (argumentOffset >= 0) {
      argument = text.subSequence(argumentOffset, caretOffset).toString();
      if (argumentOffset > 0 && text.charAt(argumentOffset - 1) == ' ') {
        if (argumentOffset - 2 >= 0 && Character.isJavaIdentifierPart(text.charAt(argumentOffset - 2))) {
          argumentOffset--;
        }
      }
    }
    List<TemplateImpl> candidatesWithArgument = findMatchingTemplates(text, argumentOffset, shortcutChar, templateSettings, true);

    if (candidatesWithArgument.isEmpty() && candidatesWithoutArgument.isEmpty()) {
      return null;
    }

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitDocument(document);
      }
    }, "", null);

    candidatesWithoutArgument = filterApplicableCandidates(file, caretOffset, candidatesWithoutArgument);
    candidatesWithArgument = filterApplicableCandidates(file, argumentOffset, candidatesWithArgument);
    Map<TemplateImpl, String> candidate2Argument = new HashMap<TemplateImpl, String>();
    addToMap(candidate2Argument, candidatesWithoutArgument, null);
    addToMap(candidate2Argument, candidatesWithArgument, argument);
    return candidate2Argument;
  }

  public boolean startNonCustomTemplates(Map<TemplateImpl, String> template2argument,
                                          Editor editor,
                                          PairProcessor<String, String> processor) {
    final int caretOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();

    if (template2argument == null || template2argument.size() == 0) {
      return false;
    }
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), myProject)) {
      return false;
    }

    if (template2argument.size() == 1) {
      TemplateImpl template = template2argument.keySet().iterator().next();
      String argument = template2argument.get(template);
      int templateStart = getTemplateStart(template, argument, caretOffset, text);
      startTemplateWithPrefix(editor, template, templateStart, processor, argument);
    }
    else {
      ListTemplatesHandler.showTemplatesLookup(myProject, editor, template2argument);
    }
    return true;
  }

  public static List<TemplateImpl> findMatchingTemplates(CharSequence text,
                                                         int caretOffset,
                                                         Character shortcutChar,
                                                         TemplateSettings settings,
                                                         boolean hasArgument) {
    String key;
    List<TemplateImpl> candidates = Collections.emptyList();
    for (int i = settings.getMaxKeyLength(); i >= 1; i--) {
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

      candidates = settings.collectMatchingCandidates(key, shortcutChar, hasArgument);
      if (!candidates.isEmpty()) break;
    }
    return candidates;
  }

  public void startTemplateWithPrefix(final Editor editor,
                                      final TemplateImpl template,
                                      @Nullable final PairProcessor<String, String> processor,
                                      @Nullable String argument) {
    final int caretOffset = editor.getCaretModel().getOffset();
    String key = template.getKey();
    int startOffset = caretOffset - key.length();
    if (argument != null) {
      if (!isDelimiter(key.charAt(key.length() - 1))) {
        // pass space
        startOffset--;
      }
      startOffset -= argument.length();
    }
    startTemplateWithPrefix(editor, template, startOffset, processor, argument);
  }

  public void startTemplateWithPrefix(final Editor editor,
                                      final TemplateImpl template,
                                      final int templateStart,
                                      @Nullable final PairProcessor<String, String> processor,
                                      @Nullable final String argument) {
    final int caretOffset = editor.getCaretModel().getOffset();
    final TemplateState templateState = initTemplateState(editor);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        editor.getDocument().deleteString(templateStart, caretOffset);
        editor.getCaretModel().moveToOffset(templateStart);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().removeSelection();
        Map<String, String> predefinedVarValues = null;
        if (argument != null) {
          predefinedVarValues = new HashMap<String, String>();
          predefinedVarValues.put(TemplateImpl.ARG, argument);
        }
        templateState.start(template, processor, predefinedVarValues);
      }
    }, CodeInsightBundle.message("insert.code.template.command"), null);
  }

  public static List<TemplateImpl> filterApplicableCandidates(PsiFile file, int caretOffset, List<TemplateImpl> candidates) {
    List<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateImpl candidate : candidates) {
      if (isApplicable(file, caretOffset - candidate.getKey().length(), candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  public TemplateContextType getContextType(@NotNull PsiFile file, int offset) {
    final TemplateContextType[] typeCollection = getAllContextTypes();
    LinkedList<TemplateContextType> userDefinedExtensionsFirst = new LinkedList<TemplateContextType>();
    for (TemplateContextType contextType : typeCollection) {
      if (contextType.getClass().getName().startsWith("com.intellij.codeInsight.template")) {
        userDefinedExtensionsFirst.addLast(contextType);
      }
      else {
        userDefinedExtensionsFirst.addFirst(contextType);
      }
    }
    for (TemplateContextType contextType : userDefinedExtensionsFirst) {
      if (contextType.isInContext(file, offset)) {
        return contextType;
      }
    }
    assert false : "OtherContextType should match any context";
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

  public static boolean isApplicable(PsiFile file, int offset, TemplateImpl template) {
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
