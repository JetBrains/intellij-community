// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExpressionTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.ui.LightweightHint;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.Callable;

public final class ShowExpressionTypeHandler implements CodeInsightActionHandler {
  private final boolean myRequestFocus;

  public ShowExpressionTypeHandler(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, @NotNull PsiFile file) {
    ThreadingAssertions.assertEventDispatchThread();

    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final Set<ExpressionTypeProvider> handlers = getHandlers(project, language, file.getViewProvider().getBaseLanguage());
    if (handlers.isEmpty()) return;

    Map<PsiElement, ExpressionTypeProvider> map = getExpressions(file, editor, handlers);
    Pass<PsiElement> callback = new Pass<>() {
      @Override
      public void pass(@NotNull PsiElement expression) {
        ExpressionTypeProvider provider = Objects.requireNonNull(map.get(expression));
        TextRange range = expression.getTextRange();
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
        // noinspection unchecked
        displayHint(new DisplayedTypeInfo(expression, provider, editor), false);
      }
    };
    if (map.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        String errorHint = Objects.requireNonNull(ContainerUtil.getFirstItem(handlers)).getErrorHint();
        Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
        HintManager.getInstance().showErrorHint(hostEditor, errorHint);
      });
    }
    else if (map.size() == 1) {
      Map.Entry<PsiElement, ExpressionTypeProvider> entry = map.entrySet().iterator().next();
      PsiElement expression = entry.getKey();
      ExpressionTypeProvider provider = entry.getValue();
      // noinspection unchecked
      DisplayedTypeInfo typeInfo = new DisplayedTypeInfo(expression, provider, editor);
      if (typeInfo.isRepeating() && provider.hasAdvancedInformation()) {
        displayHint(typeInfo, true);
      } else {
        callback.accept(expression);
      }
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor, new ArrayList<>(map.keySet()), callback,
        PsiElement::getText
      );
    }
  }

  private void displayHint(@NotNull DisplayedTypeInfo typeInfo, boolean isAdvanced) {
    Callable<@Nls String> getHintAction = () -> typeInfo.getHintText(isAdvanced);
    ReadAction.nonBlocking(getHintAction)
      .finishOnUiThread(ModalityState.any(), hint -> {
        HintManager.getInstance().setRequestFocusForNextHint(myRequestFocus);
        typeInfo.showHint(hint);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  public @NotNull Map<PsiElement, ExpressionTypeProvider> getExpressions(@NotNull PsiFile file,
                                                                         @NotNull Editor editor) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    Set<ExpressionTypeProvider> handlers = getHandlers(file.getProject(), language, file.getViewProvider().getBaseLanguage());
    return getExpressions(file, editor, handlers);
  }

  private static @NotNull Map<PsiElement, ExpressionTypeProvider> getExpressions(@NotNull PsiFile file,
                                                                                 @NotNull Editor editor,
                                                                                 @NotNull Set<? extends ExpressionTypeProvider> handlers) {
    return getExpressions(file, EditorUtil.getSelectionInAnyMode(editor), editor.getDocument(), handlers);
  }

  public static @NotNull Map<PsiElement, ExpressionTypeProvider> getExpressions(@NotNull PsiFile file,
                                                                                @NotNull TextRange range,
                                                                                @NotNull Document document,
                                                                                @NotNull Set<? extends ExpressionTypeProvider> handlers) {
    if (handlers.isEmpty()) return Collections.emptyMap();
    boolean exactRange = false;
    final Map<PsiElement, ExpressionTypeProvider> map = new LinkedHashMap<>();
    int offset = !range.isEmpty() ? range.getStartOffset() : TargetElementUtil.adjustOffset(file, document, range.getStartOffset());
    for (int i = 0; i < 3 && map.isEmpty() && offset >= i; i++) {
      PsiElement elementAt = file.findElementAt(offset - i);
      if (elementAt == null) continue;
      for (ExpressionTypeProvider handler : handlers) {
        for (PsiElement element : ((ExpressionTypeProvider<? extends PsiElement>)handler).getExpressionsAt(elementAt)) {
          TextRange r = element.getTextRange();
          if (exactRange && !r.equals(range) || !r.contains(range)) continue;
          if (!exactRange) exactRange = r.equals(range);
          map.put(element, handler);
        }
      }
    }
    return map;
  }

  public static @NotNull Set<ExpressionTypeProvider> getHandlers(final Project project, Language... languages) {
    DumbService dumbService = DumbService.getInstance(project);
    return JBIterable.of(languages).flatten(
      language -> dumbService.filterByDumbAwareness(LanguageExpressionTypes.INSTANCE.allForLanguage(language))).addAllTo(
      new LinkedHashSet<>());
  }

  static final class DisplayedTypeInfo {
    private static volatile DisplayedTypeInfo ourCurrentInstance;
    final @NotNull PsiElement myElement;
    final @NotNull ExpressionTypeProvider<PsiElement> myProvider;
    final @NotNull Editor myEditor;

    DisplayedTypeInfo(@NotNull PsiElement element, @NotNull ExpressionTypeProvider<PsiElement> provider, @NotNull Editor editor) {
      myElement = element;
      myProvider = provider;
      myEditor = editor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DisplayedTypeInfo info = (DisplayedTypeInfo)o;
      return Objects.equals(myElement, info.myElement) &&
             Objects.equals(myProvider, info.myProvider) &&
             Objects.equals(myEditor, info.myEditor);
    }

    /**
     * @return true if the same hint (i.e. on the same PsiElement, with the same provider, in the same editor) is displayed currently.
     */
    boolean isRepeating() {
      return this.equals(ourCurrentInstance);
    }

    @HintText String getHintText(boolean isAdvanced) {
      if (isAdvanced) return myProvider.getAdvancedInformationHint(myElement);
      return myProvider.getInformationHint(myElement);
    }

    void showHint(@HintText String informationHint) {
      JComponent label = HintUtil.createInformationLabel(informationHint);
      setInstance(this);
      AccessibleContextUtil.setName(label, CodeInsightBundle.message("accessible.name.expression.type.hint"));
      HintManagerImpl hintManager = (HintManagerImpl)HintManager.getInstance();
      LightweightHint hint = new LightweightHint(label);
      hint.addHintListener(e -> ApplicationManager.getApplication().invokeLater(() -> setInstance(null)));
      Editor editorToShow = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
      Point p = hintManager.getHintPosition(hint, editorToShow, HintManager.ABOVE);
      int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
      hintManager.showEditorHint(hint, editorToShow, p, flags, 0, false);
    }

    private static void setInstance(DisplayedTypeInfo typeInfo) {
      ourCurrentInstance = typeInfo;
    }
  }
}

