// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings;
import com.intellij.codeInsight.daemon.impl.analysis.JavaTelescope;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.SequencePresentation;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.SmartList;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.List;

public class JavaLensProvider implements InlayHintsProvider<JavaLensSettings> {
  private static final String CODE_LENS_ID = "JavaLens";
  public static final String FUS_GROUP_ID = "java.lens";
  private static final String USAGES_CLICKED_EVENT_ID = "usages.clicked";
  private static final String IMPLEMENTATIONS_CLICKED_EVENT_ID = "implementations.clicked";
  private static final String SETTING_CLICKED_EVENT_ID = "setting.clicked";
  private static final SettingsKey<JavaLensSettings> KEY = new SettingsKey<>(CODE_LENS_ID);

  public interface InlResult {
    void onClick(@NotNull Editor editor, @NotNull PsiElement element, MouseEvent event);

    @NotNull
    String getRegularText();
  }

  @Nullable
  @Override
  public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                             @NotNull Editor editor,
                                             @NotNull JavaLensSettings settings,
                                             @NotNull InlayHintsSink __) {
    return new FactoryInlayHintsCollector(editor) {
      @Override
      public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
        if (!(element instanceof PsiMember) || element instanceof PsiTypeParameter) return true;
        PsiElement prevSibling = element.getPrevSibling();
        if (!(prevSibling instanceof PsiWhiteSpace && prevSibling.textContains('\n'))) return true;
        PsiMember member = (PsiMember)element;
        if (member.getName() == null) return true;

        List<InlResult> hints = new SmartList<>();
        if (settings.isShowUsages()) {
          String usagesHint = JavaTelescope.usagesHint(member, file);
          if (usagesHint != null) {
            hints.add(new InlResult() {
              @Override
              public void onClick(@NotNull Editor editor, @NotNull PsiElement element, MouseEvent event) {
                FUCounterUsageLogger.getInstance().logEvent(file.getProject(), FUS_GROUP_ID, USAGES_CLICKED_EVENT_ID);
                GotoDeclarationAction.startFindUsages(editor, file.getProject(), element, new RelativePoint(event));
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
                public void onClick(@NotNull Editor editor, @NotNull PsiElement element, MouseEvent event) {
                  FeatureUsageData data = new FeatureUsageData().addData("location", "class");
                  FUCounterUsageLogger.getInstance()
                    .logEvent(file.getProject(), FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data);
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.SUBCLASSED_CLASS.getNavigationHandler();
                  navigationHandler.navigate(event, ((PsiClass)element).getNameIdentifier());
                }

                @NotNull
                @Override
                public String getRegularText() {
                  String prop = "{0, choice, 1#1 implementation|2#{0,number} implementations}";
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
                public void onClick(@NotNull Editor editor, @NotNull PsiElement element, MouseEvent event) {
                  FeatureUsageData data = new FeatureUsageData().addData("location", "method");
                  FUCounterUsageLogger.getInstance()
                    .logEvent(file.getProject(), FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data);
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.OVERRIDDEN_METHOD.getNavigationHandler();
                  navigationHandler.navigate(event, ((PsiMethod)element).getNameIdentifier());
                }

                @NotNull
                @Override
                public String getRegularText() {
                  String prop = "{0, choice, 1#1 implementation|2#{0,number} implementations}";
                  return MessageFormat.format(prop, overridings);
                }
              });
            }
          }
        }

        if (!hints.isEmpty()) {
          PresentationFactory factory = getFactory();
          Document document = editor.getDocument();
          int offset = getAnchorOffset(element);
          int line = document.getLineNumber(offset);
          int startOffset = document.getLineStartOffset(line);
          int column = offset - startOffset;
          List<InlayPresentation> presentations = new SmartList<>();
          presentations.add(factory.textSpacePlaceholder(column, true));
          for (InlResult inlResult : hints) {
            presentations.add(createPresentation(factory, element, editor, inlResult));
            presentations.add(factory.textSpacePlaceholder(1, true));
          }
          SequencePresentation shiftedPresentation = new SequencePresentation(presentations);
          InlayPresentation withSettings = addSettings(element.getProject(), factory, shiftedPresentation);
          sink.addBlockElement(startOffset, true, true, BlockInlayPriority.CODE_VISION, withSettings);
        }
        return true;
      }
    };
  }

  private static int getAnchorOffset(PsiElement element) {
    for (PsiElement child : element.getChildren()) {
      if (!(child instanceof PsiComment) && !(child instanceof PsiWhiteSpace)) {
        return child.getTextRange().getStartOffset();
      }
    }
    return element.getTextRange().getStartOffset();
  }

  @NotNull
  private static InlayPresentation createPresentation(@NotNull PresentationFactory factory,
                                                      @NotNull PsiElement element,
                                                      @NotNull Editor editor,
                                                      @NotNull InlResult result) {
    //Icon icon = AllIcons.Toolwindows.ToolWindowFind;
    //Icon icon = IconLoader.getIcon("/toolwindows/toolWindowFind_dark.svg", AllIcons.class);

    InlayPresentation text = factory.smallText(result.getRegularText());

    return factory.referenceOnHover(text, (event, translated) -> {
      result.onClick(editor, element, event);
    });
  }

  private static InlayPresentation addSettings(@NotNull Project project,
                                               @NotNull PresentationFactory factory,
                                               @NotNull InlayPresentation presentation) {
    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem item = new JMenuItem(JavaBundle.message("button.text.settings"));
    item.addActionListener(e -> {
      FUCounterUsageLogger.getInstance().logEvent(project, FUS_GROUP_ID, SETTING_CLICKED_EVENT_ID);
      InlayHintsConfigurable.showSettingsDialogForLanguage(project, JavaLanguage.INSTANCE, model -> model.getId().equals(CODE_LENS_ID));
    });
    popupMenu.add(item);

    return factory.onClick(presentation, MouseButton.Right, (e, __) -> {
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
      return Unit.INSTANCE;
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
    return JavaBundle.message("title.lenses");
  }

  @NotNull
  @Override
  public SettingsKey<JavaLensSettings> getKey() {
    return KEY;
  }

  @NotNull
  public static SettingsKey<JavaLensSettings> getSettingsKey() {
    return KEY;
  }
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
