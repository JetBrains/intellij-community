// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.codeInsight.actions.FormatChangedTextUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.testDiscovery.TestDiscoveryConfigurationProducer;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryProducer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindUtil;
import com.intellij.find.actions.CompositeActiveComponent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.rt.coverage.testDiscovery.instrumentation.TestDiscoveryInstrumentationUtils;
import com.intellij.uast.UastMetaLanguage;
import com.intellij.ui.ActiveComponent;
import com.intellij.usages.UsageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE;

public class ShowDiscoveredTestsAction extends AnAction {
  private static final String RUN_ALL_ACTION_TEXT = "Run All Affected Tests";

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(
      isEnabled(e.getProject()) &&
      (findMethodAtCaret(e) != null || e.getData(VcsDataKeys.CHANGES) != null)
    );
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    PsiMethod method = findMethodAtCaret(e);

    if (method != null) {
      showDiscoveredTestsByPsi(e, project, method);
    }
    else {
      showDiscoveredTestsByChanges(e);
    }
  }

  private static void showDiscoveredTestsByPsi(AnActionEvent e, Project project, PsiMethod method) {
    Couple<String> key = getMethodKey(method);
    if (key == null) return;
    DataContext dataContext = DataManager.getInstance().getDataContext(e.getRequiredData(EDITOR).getContentComponent());
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery");
    String presentableName =
      PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, 0);
    showDiscoveredTests(project, dataContext, presentableName, method);
  }

  private static void showDiscoveredTestsByChanges(AnActionEvent e) {
    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    Project project = e.getProject();
    assert project != null;
    showDiscoveredTestsByChanges(project, changes, "Selected Changes", e.getDataContext());
  }

  public static void showDiscoveredTestsByChanges(@NotNull Project project,
                                                  @NotNull Change[] changes,
                                                  @NotNull String title,
                                                  @NotNull DataContext dataContext) {
    PsiMethod[] asJavaMethods = findMethods(project, changes);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery.selected.changes");
    showDiscoveredTests(project, dataContext, title, asJavaMethods);
  }

  @NotNull
  public static PsiMethod[] findMethods(@NotNull Project project, @NotNull Change... changes) {
    UastMetaLanguage jvmLanguage = Language.findInstance(UastMetaLanguage.class);

    List<PsiElement> methods = FormatChangedTextUtil.getInstance().getChangedElements(project, changes, file -> {
      PsiFile psiFile = PsiUtilCore.getPsiFile(project, file);
      if (!jvmLanguage.matchesLanguage(psiFile.getLanguage())) {
        return null;
      }
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) return null;
      UFile uFile = UastContextKt.toUElement(psiFile, UFile.class);
      if (uFile == null) return null;

      PsiDocumentManager.getInstance(project).commitDocument(document);
      List<PsiElement> physicalMethods = new ArrayList<>();
      uFile.accept(new AbstractUastVisitor() {
        @Override
        public boolean visitMethod(@NotNull UMethod node) {
          physicalMethods.add(node.getSourcePsi());
          return true;
        }
      });

      return physicalMethods;
    });

    return methods
      .stream()
      .map(m -> ObjectUtils.tryCast(Objects.requireNonNull(UastContextKt.toUElement(m)).getJavaPsi(), PsiMethod.class))
      .filter(Objects::nonNull)
      .toArray(PsiMethod.ARRAY_FACTORY::create);
  }

  public static boolean isEnabled(@Nullable Project project) {
    if (project == null || DumbService.isDumb(project)) return false;
    return Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY) || ApplicationManager.getApplication().isInternal();
  }

  @Nullable
  private static PsiMethod findMethodAtCaret(@NotNull AnActionEvent e) {
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

    ActiveComponent runButton =
      createButton(RUN_ALL_ACTION_TEXT, AllIcons.Actions.Execute, () -> runAllDiscoveredTests(project, tree, ref, context, initTitle));

    Runnable pinActionListener = () -> {
      UsageView view = FindUtil.showInUsageView(null, tree.getTestMethods(), param -> param, initTitle, p -> {
        p.setCodeUsages(false); // don't show r/w, imports filtering actions
        p.setUsagesWord("test");
        p.setMergeDupLinesAvailable(false);
        p.setUsageTypeFilteringAvailable(false);
        p.setExcludeAvailable(false);
      }, project);
      if (view != null) {
        view.addButtonToLowerPane(new AbstractAction(RUN_ALL_ACTION_TEXT, AllIcons.Actions.Execute) {
          @Override
          public void actionPerformed(ActionEvent e) {
            runAllDiscoveredTests(project, tree, ref, context, initTitle);
          }
        });
      }
      JBPopup popup = ref.get();
      if (popup != null) {
        popup.cancel();
      }
    };

    KeyStroke findUsageKeyStroke = findUsagesKeyStroke();
    String pinTooltip =
      "Open Find Usages Toolwindow" + (findUsageKeyStroke == null ? "" : " " + KeymapUtil.getKeystrokeText(findUsageKeyStroke));
    ActiveComponent pinButton = createButton(pinTooltip, AllIcons.General.Pin_tab, pinActionListener);

    final PopupChooserBuilder builder =
      new PopupChooserBuilder(tree)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(new CompositeActiveComponent(pinButton))
        .setSettingButton(new CompositeActiveComponent(runButton).getComponent())
        .setItemChoosenCallback(() -> PsiNavigateUtil.navigate(tree.getSelectedElement()))
        .registerKeyboardAction(findUsageKeyStroke, __ -> pinActionListener.run())
        .setMinSize(new JBDimension(500, 300));

    JBPopup popup = builder.createPopup();
    ref.set(popup);

    TreeModel model = tree.getModel();
    if (model instanceof Disposable) {
      Disposer.register(popup, (Disposable)model);
    }

    model.addTreeModelListener(new TreeModelAdapter() {
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        int testsCount = tree.getTestCount();
        int classesCount = tree.getTestClassesCount();
        popup.setCaption("Found " + testsCount + " " +
                         StringUtil.pluralize("Test", testsCount) +
                         " in " + classesCount + " " +
                         StringUtil.pluralize("Class", classesCount) +
                         " for " + title);
      }
    });

    popup.showInBestPositionFor(dataContext);

    Runnable whenDone = () -> {
      popup.pack(true, true);
      tree.setPaintBusy(false);
    };
    processMethods(project, methods, (clazz, method, parameter) -> {
      tree.addTest(clazz, method, parameter);
      return true;
    }, whenDone);
  }

  public static void processMethods(@NotNull Project project,
                                    @NotNull PsiMethod[] methods,
                                    @NotNull TestDiscoveryProducer.PsiTestProcessor consumer,
                                    @Nullable Runnable doWhenDone) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      processMethodsInner(project, methods, consumer);
      if (doWhenDone != null) {
        EdtInvocationManager.getInstance().invokeLater(doWhenDone);
      }
    });
  }

  private static void processMethodsInner(@NotNull Project project,
                                          @NotNull PsiMethod[] methods,
                                          @NotNull TestDiscoveryProducer.PsiTestProcessor processor) {
    if (DumbService.isDumb(project)) return;
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    for (PsiMethod method : methods) {
      Couple<String> methodFqnName = ReadAction.compute(() -> getMethodKey(method));
      if (methodFqnName == null) continue;
      String fqn = methodFqnName.first;
      String methodName = methodFqnName.second;

      for (TestDiscoveryConfigurationProducer producer : getRunConfigurationProducers(project)) {
        byte frameworkId =
          ((JavaTestConfigurationBase)producer.getConfigurationFactory().createTemplateConfiguration(project)).getTestFrameworkId();
        TestDiscoveryProducer.consumeDiscoveredTests(project, fqn, methodName, frameworkId, (testClass, testMethod, parameter) -> {
          PsiClass[] testClassPsi = {null};
          PsiMethod[] testMethodPsi = {null};
          ReadAction.run(() -> {
            testClassPsi[0] = ClassUtil.findPsiClass(PsiManager.getInstance(project), testClass, null, true, scope);
            boolean checkBases = parameter != null; // check bases for parameterized tests
            if (testClassPsi[0] != null) {
              testMethodPsi[0] = ArrayUtil.getFirstElement(testClassPsi[0].findMethodsByName(testMethod, checkBases));
            }
          });
          if (testMethodPsi[0] != null) {
            if (!processor.process(testClassPsi[0], testMethodPsi[0], parameter)) return false;
          }
          return true;
        });
      }
    }
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
                                            ConfigurationContext context,
                                            String title) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Module targetModule = TestDiscoveryConfigurationProducer.detectTargetModule(tree.getContainingModules(), project);
    //first producer with results will be picked
    @SuppressWarnings("unchecked")
    Location<PsiMethod>[] testMethods = Arrays
      .stream(tree.getTestMethods())
      .map(TestMethodUsage::calculateLocation)
      .filter(Objects::nonNull)
      .toArray(Location[]::new);

    //noinspection unchecked
    getRunConfigurationProducers(project)
      .stream()
      .map(producer -> new Object() {
        TestDiscoveryConfigurationProducer myProducer = producer;
        Location<PsiMethod>[] mySupportedTests = Arrays.stream(testMethods).filter(producer::isApplicable).toArray(Location[]::new);
      })
      .max(Comparator.comparingInt(p -> p.mySupportedTests.length))
      .map(p -> p.myProducer.createProfile(p.mySupportedTests, targetModule, context, title))
      .ifPresent(profile -> {
        try {
          ExecutionEnvironmentBuilder.create(project, executor, profile).buildAndExecute();
        }
        catch (ExecutionException e) {
          ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), title, e);
        }

        JBPopup popup = ref.get();
        if (popup != null) {
          popup.cancel();
        }
      });
  }

  @Nullable
  private static Couple<String> getMethodKey(@NotNull PsiMethod method) {
    PsiClass c = method.getContainingClass();
    String fqn = c != null ? getName(c) : null;
    return fqn == null ? null : Couple.of(fqn, methodSignature(method));
  }

  @NotNull
  private static String methodSignature(@NotNull PsiMethod method) {
    return method.getName() +
           TestDiscoveryInstrumentationUtils.SEPARATOR +
           ClassUtil.getAsmMethodSignature(method);
  }

  private static String getName(PsiClass c) {
    if (c instanceof PsiAnonymousClass) {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(c, PsiClass.class);
      if (containingClass != null) {
        return ClassUtil.getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)c);
      }
    }
    return ClassUtil.getJVMClassName(c);
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
