// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings;
import com.intellij.codeInsight.daemon.impl.analysis.JavaTelescope;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.*;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.List;

public class JavaLensProvider implements InlayHintsProvider<JavaLensSettings> {
  private static final SettingsKey<JavaLensSettings> KEY = new SettingsKey<>("JavaLens");
  private static final String FUS_GROUP_ID = "java.lens";
  private static final String USAGES_CLICKED_EVENT_ID = "usages.clicked";
  private static final String IMPLEMENTATIONS_CLICKED_EVENT_ID = "implementations.clicked";
  private static final String SETTING_CLICKED_EVENT_ID = "setting.clicked";

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
                  String prop = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}";
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
          int columnWidth = EditorUtil.getPlainSpaceWidth(editor);
          int line = document.getLineNumber(offset);
          int startOffset = document.getLineStartOffset(line);
          int column = offset - startOffset;
          List<InlayPresentation> presentations = new SmartList<>();
          presentations.add(new SpacePresentation(column * columnWidth, 0));
          for (InlResult inlResult : hints) {
            presentations.add(createPresentation(factory, element, editor, inlResult));
            presentations.add(new SpacePresentation(columnWidth, 0));
          }
          SequencePresentation shiftedPresentation = new SequencePresentation(presentations);
          SpacePresentation placeholder = new SpacePresentation(columnWidth * 5, 0);
          InlayPresentation withSettingsAppearing = createTopLevelPresentation(factory,
                                                                               shiftedPresentation,
                                                                               settings(factory, element, editor),
                                                                               placeholder);
          sink.addBlockElement(startOffset, true, true, BlockInlayPriority.CODE_VISION, withSettingsAppearing);
        }
        return true;
      }
    };
  }

  private static InlayPresentation createTopLevelPresentation(@NotNull PresentationFactory factory,
                                                              @NotNull InlayPresentation shifted,
                                                              @NotNull InlayPresentation settings,
                                                              @NotNull InlayPresentation settingsPlaceholder) {
    BiStatePresentation settingsOrPlaceholder = new BiStatePresentation(() -> settings, () -> settingsPlaceholder, false) {
      @Override
      public int getWidth() {
        return Math.max(settings.getWidth(), settingsPlaceholder.getWidth());
      }

      @Override
      public int getHeight() {
        return Math.max(settings.getHeight(), settingsPlaceholder.getHeight());
      }
    };

    InlayPresentation withoutHover = factory.seq(shifted, settingsOrPlaceholder);

    return factory.onHover(withoutHover, new InlayPresentationFactory.HoverListener() {
      @Override
      public void onHover(@NotNull MouseEvent event, @NotNull Point translated) {
        settingsOrPlaceholder.setFirst();
      }

      @Override
      public void onHoverFinished() {
        settingsOrPlaceholder.setSecond();
      }
    });
  }

  private static int getAnchorOffset(PsiElement element) {
    for (PsiElement child : element.getChildren()) {
      if (!(child instanceof PsiDocComment) && !(child instanceof PsiWhiteSpace)) {
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

  @NotNull
  private static InlayPresentation settings(@NotNull PresentationFactory factory,
                                            @NotNull PsiElement element,
                                            @NotNull Editor editor) {
    return createPresentation(factory, element, editor, new InlResult() {
      @Override
      public void onClick(@NotNull Editor editor, @NotNull PsiElement element, MouseEvent event) {
        Project project = element.getProject();
        FUCounterUsageLogger.getInstance().logEvent(project, FUS_GROUP_ID, SETTING_CLICKED_EVENT_ID);
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
    return JavaBundle.message("title.lenses");
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
