// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaCodeVisionSettings;
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
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.SmartList;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class JavaCodeVisionProvider implements InlayHintsProvider<JavaCodeVisionSettings> {
  private static final String CODE_LENS_ID = "JavaLens";
  public static final String FUS_GROUP_ID = "java.lens";
  private static final String USAGES_CLICKED_EVENT_ID = "usages.clicked";
  private static final String IMPLEMENTATIONS_CLICKED_EVENT_ID = "implementations.clicked";
  private static final String SETTING_CLICKED_EVENT_ID = "setting.clicked";
  private static final SettingsKey<JavaCodeVisionSettings> KEY = new SettingsKey<>(CODE_LENS_ID);

  interface InlResult {
    void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event);

    @NotNull
    String getRegularText();
  }

  @Nullable
  @Override
  public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                             @NotNull Editor editor,
                                             @NotNull JavaCodeVisionSettings settings,
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
              public void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event) {
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
            PsiClass aClass = (PsiClass)element;
            int inheritors = JavaTelescope.collectInheritingClasses(aClass);
            if (inheritors != 0) {
              boolean isInterface = aClass.isInterface();
              hints.add(new InlResult() {
                @Override
                public void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event) {
                  FeatureUsageData data = new FeatureUsageData().addData("location", "class");
                  FUCounterUsageLogger.getInstance()
                    .logEvent(file.getProject(), FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data);
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.SUBCLASSED_CLASS.getNavigationHandler();
                  navigationHandler.navigate(event, ((PsiClass)element).getNameIdentifier());
                }

                @NotNull
                @Override
                public String getRegularText() {
                  return isInterface ? JavaBundle.message("code.vision.implementations.hint", inheritors) :
                         JavaBundle.message("code.vision.inheritors.hint", inheritors);
                }
              });
            }
          }
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)element;
            int overridings = JavaTelescope.collectOverridingMethods(method);
            if (overridings != 0) {
              boolean isAbstractMethod = isAbstractMethod(method);
              hints.add(new InlResult() {
                @Override
                public void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event) {
                  FeatureUsageData data = new FeatureUsageData().addData("location", "method");
                  FUCounterUsageLogger.getInstance()
                    .logEvent(file.getProject(), FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data);
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.OVERRIDDEN_METHOD.getNavigationHandler();
                  navigationHandler.navigate(event, ((PsiMethod)element).getNameIdentifier());
                }

                @NotNull
                @Override
                public String getRegularText() {
                  return isAbstractMethod ? JavaBundle.message("code.vision.implementations.hint", overridings) :
                         JavaBundle.message("code.vision.overrides.hint", overridings);
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

  private static boolean isAbstractMethod(@NotNull PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    PsiClass aClass = method.getContainingClass();
    return aClass != null && aClass.isInterface() && !isDefaultMethod(aClass, method);
  }

  private static boolean isDefaultMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.DEFAULT) &&
           PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8);
  }


  private static int getAnchorOffset(@NotNull PsiElement element) {
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
    InlayPresentation text = factory.smallTextWithoutBackground(result.getRegularText());

    return factory.referenceOnHover(text, (event, translated) -> result.onClick(editor, element, event));
  }

  private static @NotNull InlayPresentation addSettings(@NotNull Project project,
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
      JBPopupMenu.showByEvent(e, popupMenu);
      return Unit.INSTANCE;
    });
  }

  @NotNull
  @Override
  public JavaCodeVisionSettings createSettings() {
    return new JavaCodeVisionSettings();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("title.code.vision");
  }

  @NotNull
  @Override
  public SettingsKey<JavaCodeVisionSettings> getKey() {
    return KEY;
  }

  @NotNull
  public static SettingsKey<JavaCodeVisionSettings> getSettingsKey() {
    return KEY;
  }
  @Override
  public String getPreviewText() {
    return null;
  }

  @NotNull
  @Override
  public ImmediateConfigurable createConfigurable(@NotNull JavaCodeVisionSettings settings) {
    return new JavaCodeVisionConfigurable(settings);
  }

  @Override
  public boolean isLanguageSupported(@NotNull Language language) {
    return true;
  }

  @Override
  public boolean isVisibleInSettings() {
    return true;
  }
}
