// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.testDiscovery.TestDiscoveryConfigurationProducer;
import com.intellij.execution.testDiscovery.TestDiscoveryProducer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindUtil;
import com.intellij.find.actions.CompositeActiveComponent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ActiveComponent;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.usages.UsageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeModelAdapter;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE;

public class ShowDiscoveredTestsAction extends AnAction {
  private static final String RUN_ALL_ACTION_TEXT = "Run All";

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ApplicationManager.getApplication().isInternal() && e.getProject() != null && findMethodAtCaret(e) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    PsiMethod method = findMethodAtCaret(e);
    assert method != null;

    Couple<String> couple = getMethodQualifiedName(method);
    PsiClass c = method.getContainingClass();
    String fqn = couple != null ? couple.first : null;
    if (fqn == null || c == null) return;
    String methodName = couple.second;
    String methodPresentationName = c.getName() + "." + methodName;

    DataContext dataContext = DataManager.getInstance().getDataContext(e.getRequiredData(EDITOR).getContentComponent());
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery");
    showDiscoveredTests(project, dataContext, methodPresentationName, method);
  }

  @Nullable
  private static PsiMethod findMethodAtCaret(AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    if (editor == null || file == null) return null;
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    PsiElement prev = at == null ? null : PsiTreeUtil.prevVisibleLeaf(at);
    UMethod uMethod = UastContextKt.getUastParentOfType(prev, UMethod.class);
    return uMethod == null ? null : ObjectUtils.tryCast(uMethod.getJavaPsi(), PsiMethod.class);
  }

  static void showDiscoveredTests(@NotNull Project project,
                                  @NotNull DataContext dataContext,
                                  @NotNull String title,
                                  @NotNull PsiMethod... methods) {
    final DiscoveredTestsTree tree = new DiscoveredTestsTree(title);
    String initTitle = "Tests for " + title;

    Ref<JBPopup> ref = new Ref<>();

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);

    ActiveComponent runButton = createButton(RUN_ALL_ACTION_TEXT, AllIcons.Actions.Execute, () -> runAllDiscoveredTests(project, tree, ref, context, methods));

    Runnable pinActionListener = () -> {
      UsageView view = FindUtil.showInUsageView(null, tree.getTestMethods(), initTitle, project);
      if (view != null) {
        view.addButtonToLowerPane(new AbstractAction(RUN_ALL_ACTION_TEXT, AllIcons.Actions.Execute) {
          @Override
          public void actionPerformed(ActionEvent e) {
            runAllDiscoveredTests(project, tree, ref, context, methods);
          }
        });
        view.getPresentation().setUsagesWord("test");
        view.getPresentation().setMergeDupLinesAvailable(false);
        view.getPresentation().setUsageTypeFilteringAvailable(false);
        view.getPresentation().setExcludeAvailable(false);
      }
      JBPopup popup = ref.get();
      if (popup != null) {
        popup.cancel();
      }
    };

    KeyStroke findUsageKeyStroke = findUsagesKeyStroke();
    String pinTooltip = "Open Find Usages Toolwindow" + (findUsageKeyStroke == null ? "" : " " + KeymapUtil.getKeystrokeText(findUsageKeyStroke));
    ActiveComponent pinButton = createButton(pinTooltip, AllIcons.General.AutohideOff, pinActionListener);

    CompositeActiveComponent component = new CompositeActiveComponent(runButton, pinButton);

    final PopupChooserBuilder builder =
      new PopupChooserBuilder(tree)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(component)
        .setItemChoosenCallback(() -> PsiNavigateUtil.navigate(tree.getSelectedElement()))
        .registerKeyboardAction(findUsageKeyStroke, __ -> pinActionListener.run())
        .setMinSize(new JBDimension(500, 300));

    JBPopup popup = builder.createPopup();
    ref.set(popup);
    tree.getModel().addTreeModelListener(new TreeModelAdapter() {
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        ((AbstractPopup)popup).setCaption("Found " + tree.getTestCount() + " Tests for " + title);
      }
    });

    popup.showInBestPositionFor(dataContext);

    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (PsiMethod method : methods) {
        Couple<String> methodFqnName = ReadAction.compute(() -> getMethodQualifiedName(method));
        if (methodFqnName == null) continue;
        String fqn = methodFqnName.first;
        String methodName = methodFqnName.second;

        for (TestDiscoveryConfigurationProducer producer : getRunConfigurationProducers(project)) {
          byte frameworkId = ((JavaTestConfigurationBase)producer.getConfigurationFactory().createTemplateConfiguration(project)).getTestFrameworkId();
          TestDiscoveryProducer.consumeDiscoveredTests(project, fqn, methodName, frameworkId, (testClass, testMethod) -> {
            PsiMethod psiMethod = ReadAction.compute(() -> {
              PsiClass cc = testClass == null ? null : ClassUtil.findPsiClass(PsiManager.getInstance(project), testClass, null, true, scope);
              return cc == null ? null : ArrayUtil.getFirstElement(cc.findMethodsByName(testMethod, false));
            });
            if (psiMethod != null) {
              tree.addTest(ReadAction.compute(() -> psiMethod.getContainingClass()), psiMethod);
            }
          });
        }
      }

      EdtInvocationManager.getInstance().invokeLater(() -> {
        popup.pack(true, true);
        tree.setPaintBusy(false);
      });
    });
  }

  private static ActiveComponent createButton(String text, Icon icon, Runnable listener) {
     return new ActiveComponent.Adapter() {
      @Override
      public JComponent getComponent() {
        Presentation presentation = new Presentation();
        presentation.setText(text);
        presentation.setDescription(text);
        presentation.setIcon(icon);
        return new ActionButton(new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            listener.run();
          }
        }, presentation, "ShowDiscoveredTestsToolbar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      }
    };
  }
  private static void runAllDiscoveredTests(@NotNull Project project,
                                            DiscoveredTestsTree tree,
                                            Ref<JBPopup> ref,
                                            ConfigurationContext context, @NotNull PsiMethod[] methods) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Module targetModule = TestDiscoveryConfigurationProducer.detectTargetModule(tree.getContainingModules(), project);
    //first producer with results will be picked
    StreamEx.of(getRunConfigurationProducers(project)).cross(methods)
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

  private static List<TestDiscoveryConfigurationProducer> getRunConfigurationProducers(Project project) {
    return RunConfigurationProducer.getProducers(project)
                                   .stream()
                                   .filter(producer -> producer instanceof TestDiscoveryConfigurationProducer)
                                   .map(producer -> (TestDiscoveryConfigurationProducer)producer)
                                   .collect(Collectors.toList());
  }
}
