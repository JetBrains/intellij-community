// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.actions.FormatChangedTextUtil;
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
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collections;
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
    e.getPresentation().setEnabledAndVisible(method != null && project != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Editor editor = e.getRequiredData(EDITOR);
    PsiFile file = e.getRequiredData(PSI_FILE);
    Project project = e.getProject();
    assert project != null;

    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class);
    assert method != null;

    Couple<String> couple = getMethodQualifiedName(method);
    PsiClass c = method.getContainingClass();
    String fqn = couple != null ? couple.first : null;
    if (fqn == null || c == null) return;
    String methodName = couple.second;
    String methodPresentationName = c.getName() + "." + methodName;

    DataContext dataContext = DataManager.getInstance().getDataContext(editor.getContentComponent());
    showDiscoveredTests(project, dataContext, methodPresentationName, Collections.singletonList(method));
  }

  public static class FromChangeList extends AnAction {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      Change[] changes = e.getData(VcsDataKeys.CHANGES);

      e.getPresentation().setEnabledAndVisible(project != null && changes != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
      Project project = e.getProject();
      assert project != null;

      List<PsiMethod> methods = FormatChangedTextUtil.getInstance().getChangedElements(project, changes, file -> {
        if (file.getFileType() != JavaFileType.INSTANCE) return null;
        PsiFile psiFile = PsiUtilCore.getPsiFile(project, file);

        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return null;
        PsiDocumentManager.getInstance(project).commitDocument(document);

        return SyntaxTraverser.psiTraverser(psiFile).filter(PsiMethod.class).toList();
      });

      showDiscoveredTests(project, e.getDataContext(), "Selected Changes", methods);
    }
  }

  private static void showDiscoveredTests(@NotNull Project project,
                                          @NotNull DataContext dataContext,
                                          @NotNull String title,
                                          @NotNull List<PsiMethod> methods) {
    CollectionListModel<PsiElement> model = new CollectionListModel<>();
    final JBList<PsiElement> list = new JBList<>(model);
    //list.setFixedCellHeight();
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    String initTitle = "Tests for " + title;
    DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer();

    Ref<JBPopup> ref = new Ref<>();

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);

    InplaceButton runButton = new InplaceButton(new IconButton("Run All", AllIcons.Actions.Execute), __ -> {
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      List<Module> containingModules =
        model.getItems().stream()
             .map(element -> ModuleUtilCore.findModuleForPsiElement(element))
             .filter(module -> module != null)
             .collect(Collectors.toList());
      Module targetModule = TestDiscoveryConfigurationProducer.detectTargetModule(containingModules, project);
      //first producer with results will be picked
      StreamEx.of(getProducers(project)).cross(methods)
              .mapKeyValue((producer, method) -> producer.createDelegate(method, targetModule).findOrCreateConfigurationFromContext(context))
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

    ActionListener pinActionListener = __ -> {
      PsiElement[] elements = model.getItems().toArray(PsiElement.EMPTY_ARRAY);
      FindUtil.showInUsageView(null, elements, initTitle, project);
      JBPopup popup = ref.get();
      if (popup != null) {
        popup.cancel();
      }
    };

    KeyStroke findUsageKeyStroke = findUsagesKeyStroke();
    String pinTooltip = "Open Find Usages Toolwindow" + (findUsageKeyStroke == null ? "" : " " + KeymapUtil.getKeystrokeText(findUsageKeyStroke));
    InplaceButton pinButton = new InplaceButton(
      new IconButton(pinTooltip, AllIcons.General.AutohideOff, AllIcons.General.AutohideOffPressed, AllIcons.General.AutohideOffInactive),
      pinActionListener);

    CompositeActiveComponent component = new CompositeActiveComponent(runButton, pinButton);

    final PopupChooserBuilder builder =
      new PopupChooserBuilder(list)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(component)
        .setItemChoosenCallback(() -> PsiNavigateUtil.navigate(list.getSelectedValue()))
        .registerKeyboardAction(findUsageKeyStroke, pinActionListener)
        .setMinSize(new JBDimension(500, 300));

    renderer.installSpeedSearch(builder, true);

    JBPopup popup = builder.createPopup();
    ref.set(popup);

    list.setEmptyText("No tests captured for " + title);
    list.setPaintBusy(true);

    popup.showInBestPositionFor(dataContext);

    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    list.setCellRenderer(renderer);

    ListBackgroundUpdaterTask loadTestsTask = new ListBackgroundUpdaterTask(project, "Load tests", renderer.getComparator()) {
      @Override
      public String getCaption(int size) {
        return "Found " + size + " Tests for " + title;
      }
    };

    loadTestsTask.init((AbstractPopup)popup, list, new Ref<>());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (PsiMethod method : methods) {
        Couple<String> methodFqnName = ReadAction.compute(() -> getMethodQualifiedName(method));
        if (methodFqnName == null) continue;
        String fqn = methodFqnName.first;
        String methodName = methodFqnName.second;

        for (TestDiscoveryConfigurationProducer producer : getProducers(project)) {
          String frameworkPrefix =
            ((JavaTestConfigurationBase)producer.getConfigurationFactory().createTemplateConfiguration(project)).getFrameworkPrefix();
          TestDiscoveryProducer.consumeDiscoveredTests(project, fqn, methodName, frameworkPrefix, (testClass, testMethod) -> {
            PsiMethod psiMethod = ReadAction.compute(() -> {
              PsiClass cc = testClass == null ? null : javaFacade.findClass(testClass, scope);
              return cc == null ? null : ArrayUtil.getFirstElement(cc.findMethodsByName(testMethod, false));
            });
            if (psiMethod != null) {
              loadTestsTask.updateComponent(psiMethod);
            }
          });
        }
      }

      EdtInvocationManager.getInstance().invokeLater(() -> {
        popup.pack(true, true);
        list.setPaintBusy(false);
      });
    });
  }

  @Nullable
  private static Couple<String> getMethodQualifiedName(@NotNull PsiMethod method) {
    PsiClass c = method.getContainingClass();
    String fqn = c != null ? ClassUtil.getJVMClassName(c) : null;
    return fqn == null ? null : Couple.of(fqn, method.getName());
  }

  @Nullable
  protected static KeyStroke findUsagesKeyStroke() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
    ShortcutSet shortcutSet = action == null ? null : action.getShortcutSet();
    return shortcutSet == null ? null : KeymapUtil.getKeyStroke(shortcutSet);
  }

  private static List<TestDiscoveryConfigurationProducer> getProducers(Project project) {
    return RunConfigurationProducer.getProducers(project)
                                   .stream()
                                   .filter(producer -> producer instanceof TestDiscoveryConfigurationProducer)
                                   .map(producer -> (TestDiscoveryConfigurationProducer)producer)
                                   .collect(Collectors.toList());
  }
}
