// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.CodeStyleConfigurableWrapper;
import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.codeStyle.OtherFileTypesCodeStyleConfigurable;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
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
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public class CodeStyleStatusBarWidget extends EditorBasedStatusBarPopup implements CodeStyleSettingsListener {

  public CodeStyleStatusBarWidget(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) return WidgetState.HIDDEN;
    PsiFile psiFile = getPsiFile();
    if (psiFile == null || !psiFile.isWritable()) return WidgetState.HIDDEN;
    IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
    FileIndentOptionsProvider provider = findProvider(file, indentOptions);
    return createWidgetState(psiFile, indentOptions, provider);
  }

  @Nullable
  private static FileIndentOptionsProvider findProvider(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider optionsProvider = indentOptions.getFileIndentOptionsProvider();
    if (optionsProvider != null) return optionsProvider;
    for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensions()) {
      if (provider.areActionsAvailable(file, indentOptions)) {
        return provider;
      }
    }
    return null;
  }

  private static WidgetState createWidgetState(@NotNull PsiFile psiFile,
                                               @NotNull IndentOptions indentOptions,
                                               @Nullable FileIndentOptionsProvider provider) {
    String indentInfo = FileIndentOptionsProvider.getTooltip(indentOptions, null);
    String hint = provider != null ? provider.getHint(indentOptions) : null;
    String tooltip = "Indent: " + indentInfo + (hint != null ? " (" + hint + ")" : "");
    return new MyWidgetState(tooltip, indentInfo, psiFile, indentOptions, provider);
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
      AnAction[] actions = getActions(state.getProvider(), psiFile, state);
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
  private static AnAction[] getActions(@Nullable FileIndentOptionsProvider provider,
                                       @NotNull PsiFile psiFile,
                                       @NotNull MyWidgetState state) {
    List<AnAction> allActions = ContainerUtilRt.newArrayList();
    if (provider != null) {
      AnAction[] actions = provider.getActions(psiFile, state.getIndentOptions());
      if (actions != null) {
        allActions.addAll(Arrays.asList(actions));
      }
    }
    allActions.add(
      DumbAwareAction.create(
        ApplicationBundle.message("code.style.widget.configure.indents",psiFile.getLanguage().getDisplayName()),
        event -> {
          String id = findCodeStyleConfigurableId(psiFile);
          ShowSettingsUtilImpl.showSettingsDialog(psiFile.getProject(), id, "Tab,Indent");
        }
      )
    );
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
    return CodeStyleStatusBarWidget.class.getName();
  }

  private static class MyWidgetState extends WidgetState {

    private final @NotNull IndentOptions myIndentOptions;
    private final @Nullable FileIndentOptionsProvider myProvider;
    private final @NotNull PsiFile myPsiFile;

    protected MyWidgetState(String toolTip,
                            String text,
                            @NotNull PsiFile psiFile,
                            @NotNull IndentOptions indentOptions,
                            @Nullable FileIndentOptionsProvider provider) {
      super(toolTip, text, true);
      myIndentOptions = indentOptions;
      myProvider = provider;
      myPsiFile = psiFile;
    }

    @Nullable
    public FileIndentOptionsProvider getProvider() {
      return myProvider;
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
      FileIndentOptionsProvider provider = codeStyleWidgetState.getProvider();
      if (provider != null) {
        String message = provider.getAdvertisementText(codeStyleWidgetState.getPsiFile(), codeStyleWidgetState.getIndentOptions());
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

  @NotNull
  private static String findCodeStyleConfigurableId(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final Language language = file.getLanguage();
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
    String name = provider != null ? provider.getConfigurableDisplayName() : language.getDisplayName();
    CodeStyleSchemesConfigurable topConfigurable = new CodeStyleSchemesConfigurable(project);
    SearchableConfigurable result = topConfigurable.findSubConfigurable(name);
    if (result != null) {
      return result.getId();
    }
    return CodeStyleConfigurableWrapper.getConfigurableId(OtherFileTypesCodeStyleConfigurable.DISPLAY_NAME);
  }
}
