// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public class CodeStyleStatusBarWidget extends EditorBasedStatusBarPopup implements CodeStyleSettingsListener {
  public static final String WIDGET_ID = CodeStyleStatusBarWidget.class.getName();

  public CodeStyleStatusBarWidget(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) return WidgetState.HIDDEN;
    PsiFile psiFile = getPsiFile();
    if (psiFile == null || !psiFile.isWritable()) return WidgetState.HIDDEN;
    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
    IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
    if (settings instanceof TransientCodeStyleSettings) {
      return createWidgetState(psiFile, indentOptions, getUiContributor((TransientCodeStyleSettings)settings));
    }
    else {
      return createWidgetState(psiFile, indentOptions, getUiContributor(file, indentOptions));
    }
  }


  @Nullable
  private static CodeStyleStatusBarUIContributor getUiContributor(@NotNull TransientCodeStyleSettings settings) {
    final CodeStyleSettingsModifier modifier = settings.getModifier();
    return modifier != null ? modifier.getStatusBarUiContributor(settings) : null;
  }


  @Nullable
  private static IndentStatusBarUIContributor getUiContributor(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider provider = findProvider(file, indentOptions);
    if (provider != null) {
      return provider.getIndentStatusBarUiContributor(indentOptions);
    }
    return null;
  }

  @Nullable
  private static FileIndentOptionsProvider findProvider(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider optionsProvider = indentOptions.getFileIndentOptionsProvider();
    if (optionsProvider != null) return optionsProvider;
    for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensions()) {
      IndentStatusBarUIContributor uiContributor = provider.getIndentStatusBarUiContributor(indentOptions);
      if (uiContributor != null && uiContributor.areActionsAvailable(file)) {
        return provider;
      }
    }
    return null;
  }

  private static WidgetState createWidgetState(@NotNull PsiFile psiFile,
                                               @NotNull final IndentOptions indentOptions,
                                               @Nullable CodeStyleStatusBarUIContributor uiContributor) {
    String indentInfo = IndentStatusBarUIContributor.getTooltip(indentOptions);
    StringBuilder widgetText = new StringBuilder();
    widgetText.append(indentInfo);
    IndentOptions projectIndentOptions = CodeStyle.getSettings(psiFile.getProject()).getLanguageIndentOptions(psiFile.getLanguage());
    if (!projectIndentOptions.equals(indentOptions)) {
      widgetText.append("*");
    }
    return new MyWidgetState(getTooltip(uiContributor, indentInfo), widgetText.toString(), psiFile, indentOptions, uiContributor);
  }

  @NotNull
  private static String getTooltip(@Nullable CodeStyleStatusBarUIContributor uiContributor, @NotNull String defaultTooltip) {
    if (uiContributor != null) {
      String contributorTooltip = uiContributor.getTooltip();
      if (contributorTooltip != null) {
        return contributorTooltip;
      }
    }
    return defaultTooltip;
  }

  @Nullable
  private PsiFile getPsiFile()  {
    Editor editor = getEditor();
    Project project = getProject();
    if (editor != null && project != null) {
      Document document = editor.getDocument();
      return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    return null;
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context)
  {
    MyWidgetState state = (MyWidgetState)getWidgetState(context.getData(CommonDataKeys.VIRTUAL_FILE));
    Editor editor = getEditor();
    PsiFile psiFile = getPsiFile();
    if (state != WidgetState.HIDDEN && editor != null && psiFile != null) {
      AnAction[] actions = getActions(state.getContributor(), psiFile);
      ActionGroup actionGroup = new ActionGroup() {
        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          return actions;
        }
      };
      return JBPopupFactory.getInstance().createActionGroupPopup(
        null, actionGroup, context,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    }
    return null;
  }

  @NotNull
  private static AnAction[] getActions(@Nullable final CodeStyleStatusBarUIContributor uiContributor, @NotNull PsiFile psiFile) {
    List<AnAction> allActions = ContainerUtilRt.newArrayList();
    if (uiContributor != null) {
      AnAction[] actions = uiContributor.getActions(psiFile);
      if (actions != null) {
        allActions.addAll(Arrays.asList(actions));
      }
    }
    if (uiContributor == null ||
        (uiContributor instanceof IndentStatusBarUIContributor) &&
        ((IndentStatusBarUIContributor)uiContributor).isShowFileIndentOptionsEnabled()) {
      allActions.add(CodeStyleStatusBarWidgetProvider.createDefaultIndentConfigureAction(psiFile));
    }
    if (uiContributor != null) {
      AnAction disabledAction = uiContributor.createDisableAction(psiFile.getProject());
      if (disabledAction != null) {
        allActions.add(disabledAction);
      }
    }
    return allActions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  protected void registerCustomListeners() {
    CodeStyleSettingsManager.getInstance(getProject()).addListener(this);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    update();
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(Project project) {
    return new CodeStyleStatusBarWidget(project);
  }


  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  private static class MyWidgetState extends WidgetState {

    private final @NotNull IndentOptions myIndentOptions;
    private final @Nullable CodeStyleStatusBarUIContributor myContributor;
    private final @NotNull PsiFile myPsiFile;

    protected MyWidgetState(String toolTip,
                            String text,
                            @NotNull PsiFile psiFile,
                            @NotNull IndentOptions indentOptions,
                            @Nullable CodeStyleStatusBarUIContributor uiContributor) {
      super(toolTip, text, true);
      myIndentOptions = indentOptions;
      myContributor = uiContributor;
      myPsiFile = psiFile;
      if (uiContributor != null) {
        setIcon(uiContributor.getIcon());
      }
    }

    @Nullable
    public CodeStyleStatusBarUIContributor getContributor() {
      return myContributor;
    }

    @NotNull
    public IndentOptions getIndentOptions() {
      return myIndentOptions;
    }

    @NotNull
    public PsiFile getPsiFile() {
      return myPsiFile;
    }
  }

  @Override
  public void dispose() {
    CodeStyleSettingsManager.removeListener(myProject, this);
    super.dispose();
  }

  @Override
  protected void afterVisibleUpdate(@NotNull WidgetState state) {
    if (state instanceof MyWidgetState) {
      MyWidgetState codeStyleWidgetState = (MyWidgetState)state;
      CodeStyleStatusBarUIContributor uiContributor = codeStyleWidgetState.getContributor();
      if (uiContributor != null) {
        String message = uiContributor.getAdvertisementText(codeStyleWidgetState.getPsiFile());
        if (message != null) {
          advertise(message);
        }
      }
    }
  }

  private void advertise(@NotNull String message) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    alarm.addRequest(() -> {
      BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(message));
      JComponent statusBarComponent = getComponent();
      Balloon balloon = builder
        .setCalloutShift(statusBarComponent.getHeight() / 2)
        .setDisposable(this)
        .setHideOnClickOutside(true)
        .createBalloon();
      balloon.showInCenterOf(statusBarComponent);
    }, 500, ModalityState.NON_MODAL);
  }
}
