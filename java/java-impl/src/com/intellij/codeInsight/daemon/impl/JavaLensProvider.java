// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings;
import com.intellij.codeInsight.daemon.impl.analysis.JavaTelescope;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.List;

public class JavaLensProvider implements InlayHintsProvider<JavaLensSettings> {
  private static final SettingsKey<JavaLensSettings> KEY = new SettingsKey<>("JavaLens");

  public interface InlResult {
    void onClick(Editor editor, PsiElement element);
    String getTitle();
  }

  @Nullable
  @Override
  public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                             @NotNull Editor editor,
                                             @NotNull JavaLensSettings settings,
                                             @NotNull InlayHintsSink __) {
    PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
    return (element, editor1, sink) -> {
      if (!(element instanceof PsiMember) || element instanceof PsiTypeParameter) return true;
      PsiMember member = (PsiMember)element;
      if (member.getName() == null) return true;


      List<InlResult> hints = new SmartList<>();
      if (settings.getShowUsages()) {
        String usagesHint = JavaTelescope.usagesHint(member, file);
        if (usagesHint != null) {
          hints.add(new InlResult() {
            @Override
            public void onClick(Editor editor, PsiElement element) {
              GotoDeclarationAction.startFindUsages(editor, file.getProject(), element);
            }

            @Override
            public String getTitle() {
              return usagesHint;
            }
          });
        }
      }
      if (settings.getShowInheritors()) {
        if (element instanceof PsiClass) {
          int inheritors = JavaTelescope.collectInheritingClasses((PsiClass)element);
          if (inheritors != 0) {
            hints.add(new InlResult() {
              @Override
              public void onClick(Editor editor, PsiElement element) {
                Point point = JBPopupFactory.getInstance().guessBestPopupLocation(editor).getScreenPoint();
                MouseEvent event = new MouseEvent(new JLabel(), 0, 0, 0, point.x, point.y, 0, false);
                GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.SUBCLASSED_CLASS.getNavigationHandler();
                navigationHandler.navigate(event, ((PsiClass)element).getNameIdentifier());
              }

              @Override
              public String getTitle() {
                String prop = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}";
                return MessageFormat.format(prop, inheritors);
              }
            });
          }
        }
        if (element instanceof PsiMethod) {
          int overridings = JavaTelescope.collectOverridingMethods((PsiMethod)element);
          if (overridings != 0) {
            hints.add(new InlResult() {
              @Override
              public void onClick(Editor editor, PsiElement element) {
                Point point = JBPopupFactory.getInstance().guessBestPopupLocation(editor).getScreenPoint();
                MouseEvent event = new MouseEvent(new JLabel(), 0, 0, 0, point.x, point.y, 0, false);
                GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.OVERRIDDEN_METHOD.getNavigationHandler();
                navigationHandler.navigate(event, ((PsiMethod)element).getNameIdentifier());
              }

              @Override
              public String getTitle() {
                String prop = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}";
                return MessageFormat.format(prop, overridings);
              }
            });
          }
        }
      }

      if (!hints.isEmpty()) {
        int offset = element.getTextRange().getStartOffset();
        int line = editor1.getDocument().getLineNumber(offset);
        int lineStart = editor1.getDocument().getLineStartOffset(line);
        int indent = offset - lineStart;

        InlayPresentation[] presentations = new InlayPresentation[1 + hints.size() * 2 - 1];
        presentations[0] = factory.text(StringUtil.repeat(" ", indent));
        int o = 1;
        for (int i = 0; i < hints.size(); i++) {
          InlResult hint = hints.get(i);
          if (i != 0) {
            presentations[o++] = factory.text(" ");
          }
          presentations[o++] = createPresentation(file, factory, element, editor1, hint);
        }

        InlayPresentation presentation = factory.seq(presentations);
        sink.addBlockElement(lineStart, true, true, 0, presentation);
      }
      return true;
    };
  }

  @NotNull
  private static InlayPresentation createPresentation(@NotNull PsiFile file, @NotNull PresentationFactory factory,
                                                      @NotNull PsiElement element, @NotNull Editor editor, @NotNull InlResult result) {
    //Icon icon = AllIcons.Toolwindows.ToolWindowFind;
    Icon icon = IconLoader.getIcon("/toolwindows/toolWindowFind_dark.svg", AllIcons.class);
    //presentation = factory.withTooltip("Show usages of "+member.getName(), factory.onClick(presentation, MouseButton.Left, (_1, _2) -> {
    //  GotoDeclarationAction.startFindUsages(editor1, file.getProject(), element);
    //  return null;
    //}));


    InlayPresentation text = factory.text(result.getTitle());
    InlayPresentation presentation = factory.onClick(text, MouseButton.Left, (event, point) -> {
      result.onClick(editor, element);
      return null;
    });

    return factory.changeOnHover(presentation, () -> {
      ((EditorEx)editor).setCustomCursor(presentation, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      TextAttributes link = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR);
      return new AttributesTransformerPresentation(presentation, __ -> link);
    }, __ -> true, ()->{
      ((EditorEx)editor).setCustomCursor(presentation, null);
      return null;
    });
  }


  @NotNull
  @Override
  public JavaLensSettings createSettings() {
    return new JavaLensSettings();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return "Lenses";
  }

  @NotNull
  @Override
  public SettingsKey<JavaLensSettings> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public String getPreviewText() {
    return null;
  }

  @NotNull
  @Override
  public ImmediateConfigurable createConfigurable(@NotNull JavaLensSettings settings) {
    return new JavaLensConfigurable(settings);
  }

  @Override
  public boolean isLanguageSupported(@NotNull Language language) {
    return true;
  }

  @Override
  public boolean isVisibleInSettings() {
    return false;
  }
}
