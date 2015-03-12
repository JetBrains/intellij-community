/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
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
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class TemplateManagerImpl extends TemplateManager implements Disposable {
  protected Project myProject;
  private boolean myTemplateTesting;

  private static final Key<TemplateState> TEMPLATE_STATE_KEY = Key.create("TEMPLATE_STATE_KEY");

  public TemplateManagerImpl(Project project) {
    myProject = project;
    final EditorFactoryListener myEditorFactoryListener = new EditorFactoryAdapter() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject) return;
        if (myProject.isDisposed() || !myProject.isOpen()) return;
        TemplateState state = getTemplateState(editor);
        if (state != null) {
          state.gotoEnd();
        }
        clearTemplateState(editor);
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener, myProject);
  }

  @Override
  public void dispose() {

  }

  @TestOnly
  @Deprecated
  public void setTemplateTesting(final boolean templateTesting) {
    myTemplateTesting = templateTesting;
  }

  @TestOnly
  public static void setTemplateTesting(Project project, Disposable parentDisposable) {
    final TemplateManagerImpl instance = (TemplateManagerImpl)getInstance(project);
    instance.myTemplateTesting = true;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.myTemplateTesting = false;
      }
    });
  }

  private static void disposeState(@NotNull TemplateState state) {
    Disposer.dispose(state);
  }

  @Override
  public Template createTemplate(@NotNull String key, String group) {
    return new TemplateImpl(key, group);
  }

  @Override
  public Template createTemplate(@NotNull String key, String group, String text) {
    return new TemplateImpl(key, text, group);
  }

  @Nullable
  public static TemplateState getTemplateState(@NotNull Editor editor) {
    return editor.getUserData(TEMPLATE_STATE_KEY);
  }

  static void clearTemplateState(@NotNull Editor editor) {
    TemplateState prevState = getTemplateState(editor);
    if (prevState != null) {
      disposeState(prevState);
    }
    editor.putUserData(TEMPLATE_STATE_KEY, null);
  }

  private TemplateState initTemplateState(@NotNull Editor editor) {
    clearTemplateState(editor);
    TemplateState state = new TemplateState(myProject, editor);
    Disposer.register(this, state);
    editor.putUserData(TEMPLATE_STATE_KEY, state);
    return state;
  }

  @Override
  public boolean startTemplate(@NotNull Editor editor, char shortcutChar) {
    Runnable runnable = prepareTemplate(editor, shortcutChar, null);
    if (runnable != null) {
      runnable.run();
    }
    return runnable != null;
  }

  @Override
  public void startTemplate(@NotNull final Editor editor, @NotNull Template template) {
    startTemplate(editor, template, null);
  }

  @Override
  public void startTemplate(@NotNull Editor editor, String selectionString, @NotNull Template template) {
    startTemplate(editor, selectionString, template, true, null, null, null);
  }

  @Override
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

    //noinspection unchecked
    templateState.getProperties().put(ExpressionContext.SELECTION, selectionString);

    if (listener != null) {
      templateState.addTemplateStateListener(listener);
    }
    Runnable r = new Runnable() {
      @Override
      public void run() {
        if (selectionString != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
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

  @Override
  public void startTemplate(@NotNull final Editor editor, @NotNull final Template template, TemplateEditingListener listener) {
    startTemplate(editor, null, template, true, listener, null, null);
  }

  @Override
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

  @Nullable
  public Runnable prepareTemplate(final Editor editor, char shortcutChar, @Nullable final PairProcessor<String, String> processor) {
    if (editor.getSelectionModel().hasSelection()) {
      return null;
    }

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
    if (file == null) return null;
    TemplateSettings templateSettings = TemplateSettings.getInstance();

    Map<TemplateImpl, String> template2argument = findMatchingTemplates(file, editor, shortcutChar, templateSettings);

    for (final CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if (shortcutChar == customLiveTemplate.getShortcut()) {
        if (editor.getCaretModel().getCaretCount() > 1 && !supportsMultiCaretMode(customLiveTemplate)) {
          continue;
        }
        final Document document = editor.getDocument();
        PsiDocumentManager.getInstance(myProject).commitDocument(document);
        if (isApplicable(customLiveTemplate, editor, file)) {
          final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
          final String key = customLiveTemplate.computeTemplateKey(callback);
          if (key != null) {
            int caretOffset = editor.getCaretModel().getOffset();
            int offsetBeforeKey = caretOffset - key.length();
            CharSequence text = document.getImmutableCharSequence();
            if (template2argument == null || !containsTemplateStartingBefore(template2argument, offsetBeforeKey, caretOffset, text)) {
              return new Runnable() {
                @Override
                public void run() {
                  customLiveTemplate.expand(key, callback);
                }
              };
            }
          }
        }
      }
    }
    return startNonCustomTemplates(template2argument, editor, processor);
  }

  private static boolean supportsMultiCaretMode(CustomLiveTemplate customLiveTemplate) {
    return !(customLiveTemplate instanceof CustomLiveTemplateBase) || ((CustomLiveTemplateBase)customLiveTemplate).supportsMultiCaret();
  }

  public static boolean isApplicable(@NotNull CustomLiveTemplate customLiveTemplate,
                                     @NotNull Editor editor,
                                     @NotNull PsiFile file) {
    return isApplicable(customLiveTemplate, editor, file, false);
  }

  public static boolean isApplicable(@NotNull CustomLiveTemplate customLiveTemplate,
                                     @NotNull Editor editor,
                                     @NotNull PsiFile file, boolean wrapping) {
    return customLiveTemplate.isApplicable(file, CustomTemplateCallback.getOffset(editor), wrapping);
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
                                                          @Nullable Character shortcutChar,
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
      @Override
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

  @Nullable
  public Runnable startNonCustomTemplates(final Map<TemplateImpl, String> template2argument,
                                          final Editor editor,
                                          @Nullable final PairProcessor<String, String> processor) {
    final int caretOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    final CharSequence text = document.getCharsSequence();

    if (template2argument == null || template2argument.isEmpty()) {
      return null;
    }
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), myProject)) {
      return null;
    }

    return new Runnable() {
      @Override
      public void run() {
        if (template2argument.size() == 1) {
          TemplateImpl template = template2argument.keySet().iterator().next();
          String argument = template2argument.get(template);
          int templateStart = getTemplateStart(template, argument, caretOffset, text);
          startTemplateWithPrefix(editor, template, templateStart, processor, argument);
        }
        else {
          ListTemplatesHandler.showTemplatesLookup(myProject, editor, template2argument);
        }
      }
    };
  }

  public static List<TemplateImpl> findMatchingTemplates(CharSequence text,
                                                         int caretOffset,
                                                         @Nullable Character shortcutChar,
                                                         TemplateSettings settings,
                                                         boolean hasArgument) {
    List<TemplateImpl> candidates = Collections.emptyList();
    for (int i = settings.getMaxKeyLength(); i >= 1; i--) {
      int wordStart = caretOffset - i;
      if (wordStart < 0) {
        continue;
      }
      String key = text.subSequence(wordStart, caretOffset).toString();
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
      @Override
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

  private static List<TemplateImpl> filterApplicableCandidates(PsiFile file, int caretOffset, List<TemplateImpl> candidates) {
    if (candidates.isEmpty()) {
      return candidates;
    }

    PsiFile copy = insertDummyIdentifier(file, caretOffset, caretOffset);

    List<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateImpl candidate : candidates) {
      if (isApplicable(copy, caretOffset - candidate.getKey().length(), candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private static List<TemplateContextType> getBases(TemplateContextType type) {
    ArrayList<TemplateContextType> list = new ArrayList<TemplateContextType>();
    while (true) {
      type = type.getBaseContextType();
      if (type == null) return list;
      list.add(type);
    }
  }

  private static Set<TemplateContextType> getDirectlyApplicableContextTypes(@NotNull PsiFile file, int offset) {
    LinkedHashSet<TemplateContextType> set = new LinkedHashSet<TemplateContextType>();
    LinkedList<TemplateContextType> contexts = buildOrderedContextTypes();
    for (TemplateContextType contextType : contexts) {
      if (contextType.isInContext(file, offset)) {
        set.add(contextType);
      }
    }

    removeBases:
    while (true) {
      for (TemplateContextType type : set) {
        if (set.removeAll(getBases(type))) {
          continue removeBases;
        }
      }

      return set;
    }
  }

  private static LinkedList<TemplateContextType> buildOrderedContextTypes() {
    final TemplateContextType[] typeCollection = getAllContextTypes();
    LinkedList<TemplateContextType> userDefinedExtensionsFirst = new LinkedList<TemplateContextType>();
    for (TemplateContextType contextType : typeCollection) {
      if (contextType.getClass().getName().startsWith(Template.class.getPackage().getName())) {
        userDefinedExtensionsFirst.addLast(contextType);
      }
      else {
        userDefinedExtensionsFirst.addFirst(contextType);
      }
    }
    return userDefinedExtensionsFirst;
  }

  public static TemplateContextType[] getAllContextTypes() {
    return Extensions.getExtensions(TemplateContextType.EP_NAME);
  }

  @Override
  @Nullable
  public Template getActiveTemplate(@NotNull Editor editor) {
    final TemplateState templateState = getTemplateState(editor);
    return templateState != null ? templateState.getTemplate() : null;
  }

  public static boolean isApplicable(PsiFile file, int offset, TemplateImpl template) {
    return isApplicable(template, getApplicableContextTypes(file, offset));
  }

  public static boolean isApplicable(TemplateImpl template, Set<TemplateContextType> contextTypes) {
    for (TemplateContextType type : contextTypes) {
      if (template.getTemplateContext().isEnabled(type)) {
        return true;
      }
    }
    return false;
  }

  public static List<TemplateImpl> listApplicableTemplates(PsiFile file, int offset, boolean selectionOnly) {
    Set<TemplateContextType> contextTypes = getApplicableContextTypes(file, offset);

    final ArrayList<TemplateImpl> result = ContainerUtil.newArrayList();
    for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() && (!selectionOnly || template.isSelectionTemplate()) && isApplicable(template, contextTypes)) {
        result.add(template);
      }
    }
    return result;
  }
  
  public static List<TemplateImpl> listApplicableTemplateWithInsertingDummyIdentifier(Editor editor, PsiFile file, boolean selectionOnly) {
    int startOffset = editor.getSelectionModel().getSelectionStart();
    file = insertDummyIdentifier(editor, file);

    return listApplicableTemplates(file, startOffset, selectionOnly);
  }

  public static List<CustomLiveTemplate> listApplicableCustomTemplates(@NotNull Editor editor, @NotNull PsiFile file, boolean selectionOnly) {
    List<CustomLiveTemplate> result = new ArrayList<CustomLiveTemplate>();
    for (CustomLiveTemplate template : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if ((!selectionOnly || template.supportsWrapping()) && isApplicable(template, editor, file, selectionOnly)) {
        result.add(template);
      }
    }
    return result;
  }

  public static Set<TemplateContextType> getApplicableContextTypes(PsiFile file, int offset) {
    Set<TemplateContextType> result = getDirectlyApplicableContextTypes(file, offset);

    Language baseLanguage = file.getViewProvider().getBaseLanguage();
    if (baseLanguage != file.getLanguage()) {
      PsiFile basePsi = file.getViewProvider().getPsi(baseLanguage);
      if (basePsi != null) {
        result.addAll(getDirectlyApplicableContextTypes(basePsi, offset));
      }
    }

    // if we have, for example, a Ruby fragment in RHTML selected with its exact bounds, the file language and the base
    // language will be ERb, so we won't match HTML templates for it. but they're actually valid
    Language languageAtOffset = PsiUtilCore.getLanguageAtOffset(file, offset);
    if (languageAtOffset != file.getLanguage() && languageAtOffset != baseLanguage) {
      PsiFile basePsi = file.getViewProvider().getPsi(languageAtOffset);
      if (basePsi != null) {
        result.addAll(getDirectlyApplicableContextTypes(basePsi, offset));
      }
    }

    return result;
  }
  
  public static PsiFile insertDummyIdentifier(final Editor editor, PsiFile file) {
    boolean selection = editor.getSelectionModel().hasSelection();
    final int startOffset = selection ? editor.getSelectionModel().getSelectionStart() : editor.getCaretModel().getOffset();
    final int endOffset = selection ? editor.getSelectionModel().getSelectionEnd() : startOffset;
    return insertDummyIdentifier(file, startOffset, endOffset);
  }

  public static PsiFile insertDummyIdentifier(PsiFile file, final int startOffset, final int endOffset) {
    file = (PsiFile)file.copy();
    final Document document = file.getViewProvider().getDocument();
    assert document != null;
    WriteCommandAction.runWriteCommandAction(file.getProject(), new Runnable() {
      @Override
      public void run() {
        document.replaceString(startOffset, endOffset, CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
      }
    });

    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    return file;
  }
}
