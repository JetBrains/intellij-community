// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.find.FindUtil;
import com.intellij.find.actions.CompositeActiveComponent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE;

public class ShowDiscoveredTestsAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    Project project = e.getProject();

    PsiElement at = file == null || editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class);
    if (method == null || project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    Project project = e.getProject();

    assert project != null;

    PsiElement at = file == null || editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class);
    assert method != null;
    PsiClass c = method.getContainingClass();
    String fqn = c != null ? c.getQualifiedName() : null;
    if (fqn == null) return;
    String methodName = method.getName();
    String methodPresentationName = c.getName() + "." + methodName;

    CollectionListModel<PsiElement> model = new CollectionListModel<>();
    final JBList<PsiElement> list = new JBList<>(model);
    //list.setFixedCellHeight();
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    String initTitle = "Tests for " + methodPresentationName;
    DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer();

    Ref<JBPopup> ref = new Ref<>();

    InplaceButton runButton = new InplaceButton(new IconButton("Run All", AllIcons.Actions.Execute), __ -> {
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
      List<Module> containingModules =
        model.getItems().stream()
             .map(element -> ModuleUtilCore.findModuleForPsiElement(element))
             .filter(module -> module != null)
             .collect(Collectors.toList());
      Module targetModule = TestDiscoveryConfigurationProducer.detectTargetModule(containingModules, project);
      //first producer with results will be picked
      getProducers(project).stream()
                           .map(producer -> producer.createDelegate(method, targetModule).findOrCreateConfigurationFromContext(context))
                           .filter(Objects::nonNull)
                           .findFirst()
                           .ifPresent(configuration -> {
                             ExecutionUtil.runConfiguration(configuration.getConfigurationSettings(), executor);
                             JBPopup popup = ref.get();
                             if (popup != null) {
                               popup.cancel();
                             }
                           });
    });

    InplaceButton pinButton = new InplaceButton(
      new IconButton("Pin", AllIcons.General.AutohideOff, AllIcons.General.AutohideOffPressed, AllIcons.General.AutohideOffInactive),
      __ -> {
        PsiElement[] elements = model.getItems().toArray(PsiElement.EMPTY_ARRAY);
        FindUtil.showInUsageView(null, elements, initTitle, project);
        JBPopup popup = ref.get();
        if (popup != null) {
          popup.cancel();
        }
      });

    CompositeActiveComponent component = new CompositeActiveComponent(runButton, pinButton);

    final PopupChooserBuilder builder =
      new PopupChooserBuilder(list)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(component)
        .setItemChoosenCallback(() -> PsiNavigateUtil.navigate(list.getSelectedValue()))
        .setMinSize(new JBDimension(500, 300));

    renderer.installSpeedSearch(builder, true);

    JBPopup popup = builder.createPopup();
    ref.set(popup);

    list.setEmptyText("No tests captured for " + methodPresentationName);
    list.setPaintBusy(true);

    popup.showInBestPositionFor(editor);

    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    list.setCellRenderer(renderer);

    ListBackgroundUpdaterTask loadTestsTask = new ListBackgroundUpdaterTask(project, "Load tests", renderer.getComparator()) {
      @Override
      public String getCaption(int size) {
        return "Found " + size + " Tests for " + methodPresentationName;
      }
    };

    loadTestsTask.init((AbstractPopup)popup, list, new Ref<>());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (TestDiscoveryConfigurationProducer producer : getProducers(project)) {
        String frameworkPrefix =
          ((JavaTestConfigurationBase)producer.getConfigurationFactory().createTemplateConfiguration(project)).getFrameworkPrefix();
        TestDiscoveryProducer.consumeTestClassesAndMethods(project, fqn, methodName, frameworkPrefix, (testClassFqn, testMethodName) -> {
          PsiMethod psiMethod = ReadAction.compute(() -> {
            PsiClass cc = testClassFqn == null ? null : javaFacade.findClass(testClassFqn, scope);
            return cc == null ? null : ArrayUtil.getFirstElement(cc.findMethodsByName(testMethodName, false));
          });
          if (psiMethod != null) {
            loadTestsTask.updateComponent(psiMethod);
          }
        });
      }

      EdtInvocationManager.getInstance().invokeLater(() -> {
        popup.pack(true, true);
        list.setPaintBusy(false);
      });
    });
  }

  private static List<TestDiscoveryConfigurationProducer> getProducers(Project project) {
    return RunConfigurationProducer.getProducers(project)
                                   .stream()
                                   .filter(producer -> producer instanceof TestDiscoveryConfigurationProducer)
                                   .map(producer -> (TestDiscoveryConfigurationProducer)producer)
                                   .collect(Collectors.toList());
  }
}
