// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.analysis.JavaCodeVisionSettings;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector.CLASS_LOCATION;
import static com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector.METHOD_LOCATION;
import static com.intellij.codeInsight.hints.InlayHintsUtilsKt.addCodeVisionElement;

public class JavaCodeVisionProvider implements InlayHintsProvider<JavaCodeVisionSettings> {
  private static final String CODE_LENS_ID = "JavaLens";
  private static final SettingsKey<JavaCodeVisionSettings> KEY = new SettingsKey<>(CODE_LENS_ID);

  @Override
  public @NotNull InlayGroup getGroup() {
    return InlayGroup.CODE_VISION_GROUP;
  }

  interface InlResult {
    void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event);

    @NotNull String getRegularText();

    @NotNull String getCaseId();
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
        if (!isFirstInLine(element)) return true;
        PsiMember member = (PsiMember)element;
        if (member.getName() == null) return true;

        List<InlResult> hints = new SmartList<>();
        if (settings.isShowUsages()) {
          String usagesHint = JavaTelescope.usagesHint(member, file);
          if (usagesHint != null) {
            hints.add(new InlResult() {
              @Override
              public void onClick(@NotNull Editor editor, @NotNull PsiElement element, @NotNull MouseEvent event) {
                JavaCodeVisionUsageCollector.USAGES_CLICKED_EVENT_ID.log(element.getProject());
                GotoDeclarationAction.startFindUsages(editor, element.getProject(), element, new RelativePoint(event));
              }

              @Override
              public @NotNull String getRegularText() {
                return usagesHint;
              }

              @Override
              public @NotNull String getCaseId() {
                return JavaCodeVisionConfigurable.USAGES_CASE_ID;
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
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.SUBCLASSED_CLASS.getNavigationHandler();
                  JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.getProject(), CLASS_LOCATION);
                  navigationHandler.navigate(event, ((PsiClass)element).getNameIdentifier());
                }

                @Override
                public @NotNull String getRegularText() {
                  return isInterface ? JavaBundle.message("code.vision.implementations.hint", inheritors) :
                         JavaBundle.message("code.vision.inheritors.hint", inheritors);
                }

                @Override
                public @NotNull String getCaseId() {
                  return JavaCodeVisionConfigurable.INHERITORS_CASE_ID;
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
                  JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.getProject(), METHOD_LOCATION);
                  GutterIconNavigationHandler<PsiElement> navigationHandler = MarkerType.OVERRIDDEN_METHOD.getNavigationHandler();
                  navigationHandler.navigate(event, ((PsiMethod)element).getNameIdentifier());
                }

                @Override
                public @NotNull String getRegularText() {
                  return isAbstractMethod ? JavaBundle.message("code.vision.implementations.hint", overridings) :
                         JavaBundle.message("code.vision.overrides.hint", overridings);
                }

                @Override
                public @NotNull String getCaseId() {
                  return JavaCodeVisionConfigurable.INHERITORS_CASE_ID;
                }
              });
            }
          }
        }

        if (!hints.isEmpty()) {
          int offset = getAnchorOffset(element);

          for (InlResult inlResult : hints) {
            InlayPresentation presentation = createPresentation(getFactory(), element, editor, inlResult);
            int priority = inlResult.getCaseId() == JavaCodeVisionConfigurable.USAGES_CASE_ID
                           ? BlockInlayPriority.CODE_VISION_USAGES
                           : BlockInlayPriority.CODE_VISION_INHERITORS;

            addCodeVisionElement(sink, editor, offset, priority, presentation);
          }
        }
        return true;
      }

      private boolean isFirstInLine(PsiElement element) {
        PsiElement prevSibling = element.getPrevSibling();
        return ((prevSibling instanceof PsiWhiteSpace &&
                  (prevSibling.textContains('\n') || prevSibling.getTextRange().getStartOffset() == 0)) ||
                 element.getTextRange().getStartOffset() == 0);
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
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
    InlayPresentation presentation = factory.referenceOnHover(text, (event, translated) -> {
      PsiElement actual = pointer.getElement();
      if (actual != null) {
        result.onClick(editor, actual, event);
      }
    });
    String caseId = result.getCaseId();

    return new MenuOnClickPresentation(presentation, element.getProject(), () ->
      InlayHintsUtils.INSTANCE.getDefaultInlayHintsProviderCasePopupActions(
        KEY, JavaBundle.messagePointer("title.code.vision.inlay.hints"),
        caseId, JavaCodeVisionConfigurable.getCaseName(caseId)
      )
    );
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

  @Nls
  @Nullable
  @Override
  public String getProperty(@NotNull String key) {
    return JavaBundle.message(key);
  }

  @NotNull
  @Override
  public PsiFile createFile(@NotNull Project project,
                            @NotNull FileType fileType,
                            @NotNull Document document) {
    PsiFile file = InlayHintsProvider.super.createFile(project, fileType, document);
    file.putUserData(PsiSearchHelperImpl.USE_SCOPE_KEY, new LocalSearchScope(file));
    return file;
  }
}
