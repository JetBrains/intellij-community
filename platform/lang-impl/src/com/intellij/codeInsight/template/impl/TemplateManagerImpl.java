// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.completion.OffsetsInFile;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.codeInsight.template.impl.ListTemplatesHandler.filterTemplatesByPrefix;

public final class TemplateManagerImpl extends TemplateManager implements Disposable {
  private final @NotNull Project myProject;
  private static final Key<Boolean> ourTemplateTesting = Key.create("TemplateTesting");

  public TemplateManagerImpl(@NotNull Project project) {
    myProject = project;
    EditorFactoryListener myEditorFactoryListener = new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject) return;
        if (myProject.isDisposed() || !myProject.isOpen()) return;
        TemplateState state = getTemplateState(editor);
        if (state != null) {
          state.gotoEnd();
        }
        TemplateState prevState = clearTemplateState(editor);
        if (prevState != null) {
          Disposer.dispose(prevState);
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener, myProject);
  }

  @Override
  public void dispose() {
  }

  @TestOnly
  public static void setTemplateTesting(@NotNull Disposable parentDisposable) {
    TestModeFlags.set(ourTemplateTesting, true, parentDisposable);
  }

  @Override
  public Template createTemplate(@NotNull String key, @NotNull String group) {
    return new TemplateImpl(key, group);
  }

  @Override
  public Template createTemplate(@NotNull String key, @NotNull String group, String text) {
    return new TemplateImpl(key, text, group);
  }

  public static @Nullable TemplateState getTemplateState(@NotNull Editor editor) {
    return (TemplateState) TemplateManagerUtilBase.getTemplateState(editor);
  }

  static @Nullable TemplateState clearTemplateState(@NotNull Editor editor) {
    return (TemplateState) TemplateManagerUtilBase.clearTemplateState(editor);
  }

  private @NotNull TemplateState initTemplateState(@NotNull Editor editor) {
    Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    TemplateState prevState = clearTemplateState(topLevelEditor);
    if (prevState != null) Disposer.dispose(prevState);
    TemplateStateProcessor processor =
      editor instanceof IntentionPreviewEditor ? new NonInteractiveTemplateStateProcessor() : new InteractiveTemplateStateProcessor();
    TemplateState state = new TemplateState(myProject, topLevelEditor, topLevelEditor.getDocument(), processor);
    Disposer.register(this, state);
    TemplateManagerUtilBase.setTemplateState(topLevelEditor, state);
    return state;
  }

  @Override
  public @NotNull TemplateState runTemplate(@NotNull Editor editor, @NotNull Template template) {
    return startTemplate(editor, null, template, true, null, null, null);
  }

  @Override
  public boolean startTemplate(@NotNull Editor editor, char shortcutChar) {
    Runnable runnable = prepareTemplate(editor, shortcutChar, null);
    if (runnable != null) {
      PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
      runnable.run();
    }
    return runnable != null;
  }

  @Override
  public void startTemplate(@NotNull Editor editor, @NotNull Template template) {
    startTemplate(editor, template, null);
  }

  @Override
  public void startTemplate(@NotNull Editor editor, @Nullable String selectionString, @NotNull Template template) {
    startTemplate(editor, selectionString, template, true, null, null, null);
  }

  @Override
  public void startTemplate(@NotNull Editor editor,
                            @NotNull Template template,
                            @Nullable TemplateEditingListener listener,
                            @Nullable PairProcessor<? super String, ? super String> processor) {
    startTemplate(editor, null, template, true, listener, processor, null);
  }

  private @NotNull TemplateState startTemplate(@NotNull Editor editor,
                                               @Nullable String selectionString,
                                               @NotNull Template template,
                                               boolean inSeparateCommand,
                                               @Nullable TemplateEditingListener listener,
                                               @Nullable PairProcessor<? super String, ? super String> processor,
                                               @Nullable Map<String, String> predefinedVarValues) {
    TemplateState templateState = initTemplateState(editor);

    //noinspection unchecked
    templateState.getProperties().put(ExpressionContext.SELECTION, selectionString);

    if (listener != null) {
      templateState.addTemplateStateListener(listener);
    }
    Runnable r = () -> {
      if (selectionString != null) {
        templateState.performWrite(() -> EditorModificationUtilEx.deleteSelectedText(editor));
      }
      else {
        editor.getSelectionModel().removeSelection();
      }
      TemplateImpl substitutedTemplate = substituteTemplate((TemplateImpl)template, editor);

      templateState.start(substitutedTemplate, processor, predefinedVarValues);
    };
    if (inSeparateCommand && templateState.requiresWriteAction()) {
      CommandProcessor.getInstance().executeCommand(myProject, r, AnalysisBundle.message("insert.code.template.command"), null);
    }
    else {
      r.run();
    }

    if (shouldSkipInTests()) {
      if (!templateState.isFinished()) templateState.gotoEnd(false);
    }
    return templateState;
  }

  public boolean shouldSkipInTests() {
    return ApplicationManager.getApplication().isUnitTestMode() && !TestModeFlags.is(ourTemplateTesting);
  }

  @Override
  public void startTemplate(@NotNull Editor editor, @NotNull Template template, @Nullable TemplateEditingListener listener) {
    startTemplate(editor, null, template, true, listener, null, null);
  }

  @Override
  public void startTemplate(@NotNull Editor editor,
                            @NotNull Template template,
                            boolean inSeparateCommand,
                            @Nullable Map<String, String> predefinedVarValues,
                            @Nullable TemplateEditingListener listener) {
    startTemplate(editor, null, template, inSeparateCommand, listener, null, predefinedVarValues);
  }

  private static int passArgumentBack(@NotNull CharSequence text, int caretOffset) {
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

  private static <T, U> void addToMap(@NotNull Map<? super T, ? super U> map, @NotNull Collection<? extends T> keys, U value) {
    for (T key : keys) {
      map.put(key, value);
    }
  }

  private static boolean containsTemplateStartingBefore(@NotNull Map<TemplateImpl, String> template2argument,
                                                        int offset,
                                                        int caretOffset,
                                                        @NotNull CharSequence text) {
    for (TemplateImpl template : template2argument.keySet()) {
      String argument = template2argument.get(template);
      int templateStart = getTemplateStart(template, argument, caretOffset, text);
      if (templateStart < offset) {
        return true;
      }
    }
    return false;
  }

  public @Nullable Runnable prepareTemplate(@NotNull Editor editor, char shortcutChar, @Nullable PairProcessor<? super String, ? super String> processor) {
    if (editor.getSelectionModel().hasSelection()) {
      return null;
    }

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
    if (file == null || file instanceof PsiCompiledElement) return null;

    Map<TemplateImpl, String> template2argument = findMatchingTemplates(file, editor, shortcutChar, TemplateSettings.getInstance());
    TemplateActionContext templateActionContext = TemplateActionContext.expanding(file, editor);
    boolean multiCaretMode = editor.getCaretModel().getCaretCount() > 1;
    List<CustomLiveTemplate> customCandidates = ContainerUtil.findAll(CustomLiveTemplate.EP_NAME.getExtensions(), customLiveTemplate ->
      shortcutChar == customLiveTemplate.getShortcut() &&
      (!multiCaretMode || supportsMultiCaretMode(customLiveTemplate)) &&
      isApplicable(customLiveTemplate, templateActionContext));
    if (!customCandidates.isEmpty()) {
      int caretOffset = editor.getCaretModel().getOffset();
      CustomTemplateCallback templateCallback = new CustomTemplateCallback(editor, file);
      for (CustomLiveTemplate customLiveTemplate : customCandidates) {
        String key = customLiveTemplate.computeTemplateKey(templateCallback);
        if (key != null) {
          int offsetBeforeKey = caretOffset - key.length();
          CharSequence text = editor.getDocument().getImmutableCharSequence();
          if (template2argument == null || !containsTemplateStartingBefore(template2argument, offsetBeforeKey, caretOffset, text)) {
            return () -> {
              customLiveTemplate.expand(key, templateCallback);
              if (multiCaretMode) {
                PsiDocumentManager.getInstance(templateCallback.getProject()).commitDocument(editor.getDocument());
              }
            };
          }
        }
      }
    }

    return startNonCustomTemplates(template2argument, editor, processor);
  }

  private static boolean supportsMultiCaretMode(@NotNull CustomLiveTemplate customLiveTemplate) {
    return !(customLiveTemplate instanceof CustomLiveTemplateBase) || ((CustomLiveTemplateBase)customLiveTemplate).supportsMultiCaret();
  }

  /**
   * @implNote custom templates and callbacks require additional work. There is a single place where offset provided externally, instead
   * of using one from the callback and this is probably a mistake. If this is the case, action context may be included into the callback.
   */
  public static boolean isApplicable(@NotNull CustomLiveTemplate customLiveTemplate,
                                     @NotNull TemplateActionContext templateActionContext) {
    CustomTemplateCallback callback = new CustomTemplateCallback(Objects.requireNonNull(templateActionContext.getEditor()),
                                                                 templateActionContext.getFile());
    return customLiveTemplate.isApplicable(callback, callback.getOffset(), templateActionContext.isSurrounding());
  }

  private static int getArgumentOffset(int caretOffset, @NotNull String argument, @NotNull CharSequence text) {
    int argumentOffset = caretOffset - argument.length();
    if (argumentOffset > 0 && text.charAt(argumentOffset - 1) == ' ') {
      if (argumentOffset - 2 >= 0 && Character.isJavaIdentifierPart(text.charAt(argumentOffset - 2))) {
        argumentOffset--;
      }
    }
    return argumentOffset;
  }

  private static int getTemplateStart(@NotNull TemplateImpl template, @Nullable String argument, int caretOffset, @NotNull CharSequence text) {
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

  public Map<TemplateImpl, String> findMatchingTemplates(@NotNull PsiFile psiFile,
                                                         @NotNull Editor editor,
                                                         @Nullable Character shortcutChar,
                                                         @NotNull TemplateSettings templateSettings) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int caretOffset = editor.getCaretModel().getOffset();

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

    candidatesWithoutArgument = filterApplicableCandidates(
      TemplateActionContext.expanding(psiFile, caretOffset), candidatesWithoutArgument);
    candidatesWithArgument = filterApplicableCandidates(
      TemplateActionContext.expanding(psiFile, argumentOffset), candidatesWithArgument);
    Map<TemplateImpl, String> candidate2Argument = new HashMap<>();
    addToMap(candidate2Argument, candidatesWithoutArgument, null);
    addToMap(candidate2Argument, candidatesWithArgument, argument);
    return candidate2Argument;
  }

  public @Nullable Runnable startNonCustomTemplates(@Nullable Map<TemplateImpl, String> template2argument,
                                                    @NotNull Editor editor,
                                                    @Nullable PairProcessor<? super String, ? super String> processor) {
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();

    if (template2argument == null || template2argument.isEmpty()) {
      return null;
    }

    return () -> {
      if (template2argument.size() == 1) {
        TemplateImpl template = template2argument.keySet().iterator().next();
        String argument = template2argument.get(template);
        int templateStart = getTemplateStart(template, argument, caretOffset, text);
        startTemplateWithPrefix(editor, template, templateStart, processor, argument);
      }
      else {
        ListTemplatesHandler.showTemplatesLookup(myProject, editor, template2argument);
      }
    };
  }

  @NotNull
  private static List<TemplateImpl> findMatchingTemplates(@NotNull CharSequence text,
                                                          int caretOffset,
                                                          @Nullable Character shortcutChar,
                                                          @NotNull TemplateSettings settings,
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

  public void startTemplateWithPrefix(@NotNull Editor editor,
                                      @NotNull TemplateImpl template,
                                      @Nullable PairProcessor<? super String, ? super String> processor,
                                      @Nullable String argument) {
    int caretOffset = editor.getCaretModel().getOffset();
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

  private @NotNull TemplateImpl substituteTemplate(@NotNull TemplateImpl template, @NotNull Editor editor) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return template;
    }
    for (TemplateSubstitutor substitutor : TemplateSubstitutor.EP_NAME.getExtensionList()) {
      TemplateImpl substituted = substitutor.substituteTemplate(new TemplateSubstitutionContext(myProject, editor), template);
      if (substituted != null) {
        template = substituted;
      }
    }
    return template;
  }

  public void startTemplateWithPrefix(@NotNull Editor editor,
                                      @NotNull TemplateImpl template,
                                      int templateStart,
                                      @Nullable PairProcessor<? super String, ? super String> processor,
                                      @Nullable String argument) {
    int caretOffset = editor.getCaretModel().getOffset();
    TemplateState templateState = initTemplateState(editor);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      editor.getDocument().deleteString(templateStart, caretOffset);
      editor.getCaretModel().moveToOffset(templateStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      Map<String, String> predefinedVarValues = null;
      if (argument != null) {
        predefinedVarValues = new HashMap<>();
        predefinedVarValues.put(TemplateImpl.ARG, argument);
      }
      templateState.start(substituteTemplate(template, editor), processor, predefinedVarValues);
    }, AnalysisBundle.message("insert.code.template.command"), null);
  }

  private static @NotNull List<TemplateImpl> filterApplicableCandidates(@NotNull TemplateActionContext templateActionContext,
                                                                        @NotNull List<TemplateImpl> candidates) {
    if (candidates.isEmpty()) {
      return candidates;
    }

    PsiFile copy = insertDummyIdentifierWithCache(templateActionContext).getFile();

    List<TemplateImpl> result = new ArrayList<>();
    for (TemplateImpl candidate : candidates) {
      if (isApplicable(candidate, TemplateActionContext.expanding(
        copy, templateActionContext.getStartOffset() - candidate.getKey().length()))) {
        result.add(candidate);
      }
    }
    return result;
  }

  @NotNull
  private static List<TemplateContextType> getBases(@NotNull TemplateContextType type) {
    List<TemplateContextType> list = new ArrayList<>();
    while (true) {
      type = type.getBaseContextType();
      if (type == null) return list;
      list.add(type);
    }
  }

  @NotNull
  private static Set<TemplateContextType> getDirectlyApplicableContextTypes(@NotNull TemplateActionContext templateActionContext) {
    Set<TemplateContextType> set = new LinkedHashSet<>();
    for (TemplateContextType contextType : getAllContextTypes()) {
      if (contextType.isInContext(templateActionContext)) {
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

  public static @Unmodifiable @NotNull List<TemplateContextType> getAllContextTypes() {
    return TemplateContextTypes.getAllContextTypes();
  }

  @Override
  public @Nullable Template getActiveTemplate(@NotNull Editor editor) {
    TemplateState templateState = getTemplateState(editor);
    return templateState != null ? templateState.getTemplate() : null;
  }

  @Override
  public boolean finishTemplate(@NotNull Editor editor) {
    TemplateState state = getTemplateState(editor);
    if (state != null) {
      state.gotoEnd();
      return true;
    }
    return false;
  }

  /**
   * @deprecated use {@link #isApplicable(TemplateImpl, TemplateActionContext)}
   */
  @Deprecated(forRemoval = true)
  public static boolean isApplicable(@NotNull PsiFile file, int offset, TemplateImpl template) {
    return isApplicable(template, TemplateActionContext.expanding(file, offset));
  }

  public static boolean isApplicable(@NotNull TemplateImpl template, @NotNull TemplateActionContext templateActionContext) {
    return isApplicable(template, getApplicableContextTypes(templateActionContext));
  }

  public static boolean isApplicable(@NotNull TemplateImpl template, @NotNull Set<? extends TemplateContextType> contextTypes) {
    for (TemplateContextType type : contextTypes) {
      if (template.getTemplateContext().isEnabled(type)) {
        return true;
      }
    }
    return false;
  }

  public static @NotNull List<TemplateImpl> listApplicableTemplates(@NotNull TemplateActionContext templateActionContext) {
    Set<TemplateContextType> contextTypes = getApplicableContextTypes(templateActionContext);

    ArrayList<TemplateImpl> result = new ArrayList<>();
    for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() &&
          (!templateActionContext.isSurrounding() || template.isSelectionTemplate()) &&
          isApplicable(template, contextTypes)) {
        result.add(template);
      }
    }
    return result;
  }

  public static @NotNull List<TemplateImpl> listApplicableTemplateWithInsertingDummyIdentifier(@NotNull TemplateActionContext templateActionContext) {
    OffsetsInFile offsets = insertDummyIdentifierWithCache(templateActionContext);
    return listApplicableTemplates(TemplateActionContext.create(
      offsets.getFile(), null, getStartOffset(offsets), getEndOffset(offsets), templateActionContext.isSurrounding()));
  }

  public static @NotNull List<CustomLiveTemplate> listApplicableCustomTemplates(@NotNull TemplateActionContext templateActionContext) {
    List<CustomLiveTemplate> result = new ArrayList<>();
    for (CustomLiveTemplate template : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if ((!templateActionContext.isSurrounding() || template.supportsWrapping()) && isApplicable(template, templateActionContext)) {
        result.add(template);
      }
    }
    return result;
  }

  /**
   * Checks if there is an applicable fully typed template or custom template in the editor
   *
   * @param file current file
   * @param editor current editor
   * @return {@code true} if a template is typed in the editor
   */
  @ApiStatus.Internal
  public static boolean isApplicableTemplatePresent(@NotNull PsiFile file, @NotNull Editor editor) {
    TemplateActionContext context = TemplateActionContext.expanding(file, editor);
    int offset = editor.getCaretModel().getOffset();
    Map<TemplateImpl, String> templates = filterTemplatesByPrefix(listApplicableTemplates(context), editor, offset, true, false);
    if (!templates.isEmpty()) {
      return true;
    }

    CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
    for (CustomLiveTemplate template : listApplicableCustomTemplates(context)) {
      if (template.computeTemplateKey(callback) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static Set<TemplateContextType> getApplicableContextTypes(@NotNull TemplateActionContext templateActionContext) {
    Set<TemplateContextType> result = getDirectlyApplicableContextTypes(templateActionContext);

    PsiFile file = templateActionContext.getFile();
    Language baseLanguage = file.getViewProvider().getBaseLanguage();
    if (baseLanguage != file.getLanguage()) {
      PsiFile basePsi = file.getViewProvider().getPsi(baseLanguage);
      if (basePsi != null) {
        result.addAll(getDirectlyApplicableContextTypes(templateActionContext.withFile(basePsi)));
      }
    }

    // if we have, for example, a Ruby fragment in ERB selected with its exact bounds, the file language and the base
    // language will be ERB, so we won't match HTML templates for it. but they're actually valid
    Language languageAtOffset = PsiUtilCore.getLanguageAtOffset(file, templateActionContext.getStartOffset());
    if (languageAtOffset != file.getLanguage() && languageAtOffset != baseLanguage) {
      PsiFile basePsi = file.getViewProvider().getPsi(languageAtOffset);
      if (basePsi != null) {
        result.addAll(getDirectlyApplicableContextTypes(templateActionContext.withFile(basePsi)));
      }
    }

    return result;
  }

  private static final OffsetKey START_OFFSET = OffsetKey.create("start", false);
  private static final OffsetKey END_OFFSET = OffsetKey.create("end", true);

  private static int getStartOffset(@NotNull OffsetsInFile offsets) {
    return offsets.getOffsets().getOffset(START_OFFSET);
  }

  private static int getEndOffset(@NotNull OffsetsInFile offsets) {
    return offsets.getOffsets().getOffset(END_OFFSET);
  }

  private static OffsetsInFile insertDummyIdentifierWithCache(@NotNull TemplateActionContext templateActionContext) {
    ProperTextRange editRange = ProperTextRange.create(templateActionContext.getStartOffset(), templateActionContext.getEndOffset());
    PsiFile file = templateActionContext.getFile();
    assertRangeWithinDocument(editRange, file.getFileDocument());

    ConcurrentMap<Pair<ProperTextRange, String>, OffsetsInFile> map = CachedValuesManager.getCachedValue(file, () ->
      CachedValueProvider.Result.create(
        ConcurrentFactoryMap.createMap(
          key -> copyWithDummyIdentifier(new OffsetsInFile(file), key.first.getStartOffset(), key.first.getEndOffset(), key.second)),
        file, file.getFileDocument()));
    return map.get(Pair.create(editRange, CompletionUtil.DUMMY_IDENTIFIER_TRIMMED));
  }

  private static void assertRangeWithinDocument(@NotNull ProperTextRange editRange, @NotNull Document document) {
    TextRange docRange = TextRange.from(0, document.getTextLength());
    assert docRange.contains(editRange) : docRange + " doesn't contain " + editRange;
  }

  public static @NotNull OffsetsInFile copyWithDummyIdentifier(@NotNull OffsetsInFile offsetMap, int startOffset, int endOffset, @NotNull String replacement) {
    offsetMap.getOffsets().addOffset(START_OFFSET, startOffset);
    offsetMap.getOffsets().addOffset(END_OFFSET, endOffset);

    Document document = offsetMap.getFile().getFileDocument();
    assert document != null;
    if (replacement.isEmpty() &&
        startOffset == endOffset &&
        PsiDocumentManager.getInstance(offsetMap.getFile().getProject()).isCommitted(document)) {
      return offsetMap;
    }

    OffsetsInFile hostOffsets = offsetMap.toTopLevelFile();
    OffsetsInFile hostCopy = hostOffsets.copyWithReplacement(getStartOffset(hostOffsets), getEndOffset(hostOffsets), replacement);
    return hostCopy.toInjectedIfAny(getStartOffset(hostCopy));
  }
}
