// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings;
import com.intellij.codeInsight.daemon.impl.analysis.JavaTelescope;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

public class JavaLensProvider implements InlayHintsProvider<JavaLensSettings>, EditorMouseMotionListener {
  private static final SettingsKey<JavaLensSettings> KEY = new SettingsKey<>("JavaLens");

  public JavaLensProvider() {
    ApplicationManager.getApplication().getMessageBus()
    .connect().subscribe(JavaLensSettings.JAVA_LENS_SETTINGS_CHANGED, settings->{
      if (settings.isShowUsages() || settings.isShowImplementations()) {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(this, ApplicationManager.getApplication());
      }
      else {
        EditorFactory.getInstance().getEventMulticaster().removeEditorMouseMotionListener(this);
      }
    });
  }

  public interface InlResult {
    void onClick(@NotNull Editor editor, @NotNull PsiElement element);
    @NotNull
    String getRegularText();

    @NotNull
    default String getHoverText() { return getRegularText(); }
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
      if (settings.isShowUsages()) {
        String usagesHint = JavaTelescope.usagesHint(member, file);
        if (usagesHint != null) {
          hints.add(new InlResult() {
            @Override
            public void onClick(@NotNull Editor editor, @NotNull PsiElement element) {
              GotoDeclarationAction.startFindUsages(editor, file.getProject(), element);
            }

            @NotNull
            @Override
            public String getRegularText() {
              return usagesHint;
            }
          });
        }
      }
      if (settings.isShowImplementations()) {
        if (element instanceof PsiClass) {
          int inheritors = JavaTelescope.collectInheritingClasses((PsiClass)element);
          if (inheritors != 0) {
            hints.add(new InlResult() {
              @Override
              public void onClick(@NotNull Editor editor, @NotNull PsiElement element) {
                Point point = JBPopupFactory.getInstance().guessBestPopupLocation(editor).getScreenPoint();
                MouseEvent event = new MouseEvent(new JLabel(), 0, 0, 0, point.x, point.y, 0, false);
                GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.SUBCLASSED_CLASS.getNavigationHandler();
                navigationHandler.navigate(event, ((PsiClass)element).getNameIdentifier());
              }

              @NotNull
              @Override
              public String getRegularText() {
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
              public void onClick(@NotNull Editor editor, @NotNull PsiElement element) {
                Point point = JBPopupFactory.getInstance().guessBestPopupLocation(editor).getScreenPoint();
                MouseEvent event = new MouseEvent(new JLabel(), 0, 0, 0, point.x, point.y, 0, false);
                GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.OVERRIDDEN_METHOD.getNavigationHandler();
                navigationHandler.navigate(event, ((PsiMethod)element).getNameIdentifier());
              }

              @NotNull
              @Override
              public String getRegularText() {
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

        InlayPresentation[] presentations = new InlayPresentation[hints.size() * 2 + 1];
        presentations[0] = factory.text(StringUtil.repeat(" ", indent));
        int o = 1;
        for (int i = 0; i < hints.size(); i++) {
          InlResult hint = hints.get(i);
          if (i != 0) {
            presentations[o++] = factory.text(" ");
          }
          presentations[o++] = createPresentation(factory, element, editor1, hint);
        }
        presentations[o] = factory.text("          "); // placeholder for "Settings..."

        InlayPresentation seq = factory.seq(presentations);
        InlayPresentation withAppearingSettings = factory.changeOnHover(seq, () -> {
          InlayPresentation[] trimmedSpace = Arrays.copyOf(presentations, presentations.length - 1);
          InlayPresentation[] spaceAndSettings = {factory.text("  "), settings(factory, element, editor)};
          InlayPresentation[] withSettings = ArrayUtil.mergeArrays(trimmedSpace, spaceAndSettings);
          return factory.seq(withSettings);
        }, e -> true);
        sink.addBlockElement(lineStart, true, true, 0, withAppearingSettings);
      }
      return true;
    };
  }

  @NotNull
  private static InlayPresentation createPresentation(@NotNull PresentationFactory factory,
                                                      @NotNull PsiElement element,
                                                      @NotNull Editor editor,
                                                      @NotNull InlResult result) {
    //Icon icon = AllIcons.Toolwindows.ToolWindowFind;
    //Icon icon = IconLoader.getIcon("/toolwindows/toolWindowFind_dark.svg", AllIcons.class);

    InlayPresentation text = factory.smallText(result.getRegularText());

    return factory.changeOnHover(text, () -> {
      InlayPresentation onClick = factory.onClick(text, MouseButton.Left, (___, __) -> {
        result.onClick(editor, element);
        return null;
      });
      return referenceColor(onClick);
    }, __ -> true);
  }

  @NotNull
  private static InlayPresentation referenceColor(@NotNull InlayPresentation presentation) {
    return new AttributesTransformerPresentation(presentation,
           __ -> {
             TextAttributes attributes =
               EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).clone();
             attributes.setEffectType(EffectType.LINE_UNDERSCORE);
             return attributes;
           });
  }

  @NotNull
  private static InlayPresentation settings(@NotNull PresentationFactory factory,
                                            @NotNull PsiElement element,
                                            @NotNull Editor editor) {
    return createPresentation(factory, element, editor, new InlResult() {
      @Override
      public void onClick(@NotNull Editor editor, @NotNull PsiElement element) {
        Project project = element.getProject();
        InlayHintsConfigurable.showSettingsDialogForLanguage(project, element.getLanguage());
      }

      @NotNull
      @Override
      public String getRegularText() {
        return "Settings...";
      }
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

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    Point point = e.getMouseEvent().getPoint();
    Editor editor = e.getEditor();
    boolean hoverOverJavaLens = isHoverOverJavaLens(editor, point);
    Cursor cursor = hoverOverJavaLens ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null;
    ((EditorEx)editor).setCustomCursor(this, cursor);
  }

  private static boolean isHoverOverJavaLens(@NotNull Editor editor, @NotNull Point point) {
    InlayModel inlayModel = editor.getInlayModel();
    Inlay at = inlayModel.getElementAt(point);
    return at != null && InlayHintsSinkImpl.Companion.getSettingsKey(at) == KEY;
  }
}
