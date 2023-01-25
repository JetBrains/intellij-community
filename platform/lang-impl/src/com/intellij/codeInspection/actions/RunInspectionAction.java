// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionOptionPaneRenderer;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameFilter;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.InspectionUiUtilKt;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class RunInspectionAction extends GotoActionBase implements DataProvider {
  private static final Logger LOGGER = Logger.getInstance(RunInspectionAction.class);
  private final String myPredefinedText;

  @SuppressWarnings("unused")
  public RunInspectionAction() {
    this(null);
  }

  public RunInspectionAction(String predefinedText) {
    myPredefinedText = predefinedText;
    getTemplatePresentation().setText(IdeBundle.messagePointer("goto.inspection.action.text"));
  }

  @Override
  protected void gotoActionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    final VirtualFile[] virtualFiles = ObjectUtils.notNull(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY), VirtualFile.EMPTY_ARRAY);

    final GotoInspectionModel model = new GotoInspectionModel(project);
    showNavigationPopup(e, model, new GotoActionCallback<>() {
      @Override
      protected ChooseByNameFilter<Object> createFilter(@NotNull ChooseByNamePopup popup) {
        popup.setSearchInAnyPlace(true);
        return super.createFilter(popup);
      }

      @Override
      public void elementChosen(ChooseByNamePopup popup, final Object element) {
        ApplicationManager.getApplication().invokeLater(
          () -> runInspection(project, (((InspectionElement)element)).getToolWrapper().getShortName(), virtualFiles, psiElement, psiFile));
      }
    }, false);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return PlatformDataKeys.PREDEFINED_TEXT.is(dataId) ? myPredefinedText : null;
  }

  public static void runInspection(@NotNull Project project,
                                   @NotNull String shortName,
                                   @Nullable VirtualFile virtualFile,
                                   @Nullable PsiElement psiElement,
                                   @Nullable PsiFile psiFile) {
    runInspection(project, shortName, virtualFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[] {virtualFile} , psiElement, psiFile);
  }

  public static void runInspection(@NotNull Project project,
                                   @NotNull String shortName,
                                   @NotNull VirtualFile @NotNull [] virtualFiles,
                                   @Nullable PsiElement psiElement,
                                   @Nullable PsiFile psiFile) {
    final PsiElement element = psiFile == null ? psiElement : psiFile;
    final InspectionProfile currentProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper<?, ?> toolWrapper = element != null ? currentProfile.getInspectionTool(shortName, element)
                                                                    : currentProfile.getInspectionTool(shortName, project);
    LOGGER.assertTrue(toolWrapper != null, "Missed inspection: " + shortName);

    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Module module = findModuleForFiles(project, virtualFiles);

    AnalysisScope analysisScope = null;
    if (psiFile != null) {
      analysisScope = new AnalysisScope(psiFile);
    }
    else {
      if (virtualFiles.length == 1 && virtualFiles[0].isDirectory()) {
        final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFiles[0]);
        if (psiDirectory != null) {
          analysisScope = new AnalysisScope(psiDirectory);
        }
      }
      if (analysisScope == null && virtualFiles.length != 0) {
        analysisScope = new AnalysisScope(project, ContainerUtil.newHashSet(virtualFiles));
      }
      if (analysisScope == null) {
        analysisScope = new AnalysisScope(project);
      }
    }

    final AnalysisUIOptions options = AnalysisUIOptions.getInstance(project);
    final FileFilterPanel fileFilterPanel = new FileFilterPanel();
    fileFilterPanel.init(options);

    final AnalysisScope initialAnalysisScope = analysisScope;
    List<ModelScopeItem> items = BaseAnalysisActionDialog.standardItems(project, analysisScope, module, psiElement);
    final BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(IdeBundle.message("goto.inspection.action.dialog.title", toolWrapper.getDisplayName()),
                                                                         CodeInsightBundle.message("analysis.scope.title", InspectionsBundle
                                                                           .message("inspection.action.noun")), project,
                                                                         items, options, true) {

      private InspectionToolWrapper<?, ?> myUpdatedSettingsToolWrapper;

      @Override
      protected @NotNull JComponent getAdditionalActionSettings(@NotNull Project project) {
        final JPanel panel = new JPanel(new GridBagLayout());
        final boolean hasOptionsPanel = InspectionOptionPaneRenderer.hasSettings(toolWrapper.getTool());
        final GridBag constraints = new GridBag()
          .setDefaultWeightX(1)
          .setDefaultWeightY(hasOptionsPanel ? 0 : 1)
          .setDefaultFill(GridBagConstraints.HORIZONTAL);

        panel.add(fileFilterPanel.getPanel(), constraints.nextLine());

        if (hasOptionsPanel) {
          myUpdatedSettingsToolWrapper = copyToolWithSettings(toolWrapper);
          final JComponent optionsPanel = InspectionOptionPaneRenderer.createOptionsPanel(myUpdatedSettingsToolWrapper.getTool(), myDisposable, project);
          LOGGER.assertTrue(optionsPanel != null);

          final var separator = new TitledSeparator(IdeBundle.message("goto.inspection.action.choose.inherit.settings.from"));
          separator.setBorder(JBUI.Borders.empty());
          panel.add(separator, constraints.nextLine().insetTop(20));

          optionsPanel.setBorder(InspectionUiUtilKt.getBordersForOptions(optionsPanel));
          final var scrollPane = InspectionUiUtilKt.addScrollPaneIfNecessary(optionsPanel);
          final var preferredSize = scrollPane.getPreferredSize();
          scrollPane.setPreferredSize(new Dimension(preferredSize.width, Math.min(preferredSize.height, 400)));
          panel.add(scrollPane, constraints.nextLine());
        }

        return panel;
      }

      @NotNull
      @Override
      public AnalysisScope getScope(@NotNull AnalysisScope defaultScope) {
        final AnalysisScope scope = super.getScope(defaultScope);
        final GlobalSearchScope filterScope = fileFilterPanel.getSearchScope();
        if (filterScope == null) {
          return scope;
        }
        scope.setFilter(filterScope);
        return scope;
      }

      private AnalysisScope getScope() {
        return getScope(initialAnalysisScope);
      }

      private InspectionToolWrapper<?, ?> getToolWrapper() {
        return myUpdatedSettingsToolWrapper == null ? toolWrapper : myUpdatedSettingsToolWrapper;
      }

      @Override
      protected Action @NotNull [] createActions() {
        final List<Action> actions = new ArrayList<>();
        final boolean hasFixAll = toolWrapper.getTool() instanceof CleanupLocalInspectionTool;
        actions.add(new AbstractAction(hasFixAll ? CodeInsightBundle.message("action.analyze.verb")
                                                 : CommonBundle.getOkButtonText()) {
          {
            putValue(DEFAULT_ACTION, Boolean.TRUE);
          }
          @Override
          public void actionPerformed(ActionEvent e) {
            AnalysisScope scope = getScope();
            InspectionToolWrapper<?, ?> wrapper = getToolWrapper();
            DumbService.getInstance(project).smartInvokeLater(() -> RunInspectionIntention.rerunInspection(wrapper, managerEx, scope, null));
            close(DialogWrapper.OK_EXIT_CODE);
          }
        });
        if (hasFixAll) {
          actions.add(new AbstractAction(IdeBundle.message("goto.inspection.action.fix.all")) {
            @Override
            public void actionPerformed(ActionEvent e) {
              InspectionToolWrapper<?, ?> wrapper = getToolWrapper();
              InspectionProfileImpl cleanupToolProfile = RunInspectionIntention.createProfile(wrapper, managerEx, null);
              managerEx.createNewGlobalContext()
                .codeCleanup(getScope(), cleanupToolProfile, "Cleanup by " + wrapper.getDisplayName(), null, false);
              close(DialogWrapper.OK_EXIT_CODE);
            }
          });
        }
        actions.add(getCancelAction());
        if (SystemInfo.isMac) {
          Collections.reverse(actions);
        }
        return actions.toArray(new Action[0]);
      }
    };

    //don't show if called for regexp inspection which makes no sense without injection
    dialog.setShowInspectInjectedCode(!(Language.findLanguageByID(toolWrapper.getLanguage()) instanceof InjectableLanguage));
    dialog.showAndGet();
  }

  @Nullable
  private static Module findModuleForFiles(@NotNull Project project, VirtualFile @NotNull [] files) {
    Set<Module> modules = ContainerUtil.map2Set(files, f -> ModuleUtilCore.findModuleForFile(f, project));
    return ContainerUtil.getFirstItem(modules);
  }

  private static InspectionToolWrapper<?, ?> copyToolWithSettings(@NotNull final InspectionToolWrapper tool) {
    final Element options = new Element("copy");
    tool.getTool().writeSettings(options);
    final InspectionToolWrapper<?, ?> copiedTool = tool.createCopy();
    copiedTool.getTool().readSettings(options);
    return copiedTool;
  }
}
