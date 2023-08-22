// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.codeInsight.actions.VcsFacadeImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationWithDiscoverySupport;
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
import com.intellij.ide.IdeBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.rt.coverage.testDiscovery.instrumentation.TestDiscoveryInstrumentationUtils;
import com.intellij.uast.UastMetaLanguage;
import com.intellij.ui.ActiveComponent;
import com.intellij.usages.UsageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;
import static com.intellij.openapi.util.Pair.pair;

public class ShowAffectedTestsAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(
      isEnabled(e.getProject()) &&
      (findMethodAtCaret(e) != null ||
       findClassAtCaret(e) != null ||
       !findFilesInContext(e).isEmpty() ||
       e.getData(VcsDataKeys.CHANGES) != null)
    );
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    PsiMethod method = findMethodAtCaret(e);
    if (method != null) {
      showDiscoveredTestsByPsiMethod(project, method, e);
      return;
    }

    PsiClass psiClass = findClassAtCaret(e);
    if (psiClass != null) {
      showDiscoveredTestsByPsiClass(project, psiClass, e);
      return;
    }

    if (e.getData(VcsDataKeys.CHANGES) != null) {
      showDiscoveredTestsByChanges(e);
      return;
    }

    List<VirtualFile> virtualFiles = findFilesInContext(e);
    if (!virtualFiles.isEmpty()) {
      showDiscoveredTestsByFile(project, virtualFiles, e);
    }
  }

  private static void showDiscoveredTestsByFile(@NotNull Project project, @NotNull List<? extends VirtualFile> files, @NotNull AnActionEvent e) {
    VirtualFile projectBasePath = getBasePathAsVirtualFile(project);
    if (projectBasePath == null) return;
    DiscoveredTestsTree tree = showTree(project, e.getDataContext(), createTitle(files), e.getPlace());
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery");

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      JBIterable<String> paths = JBIterable
        .from(files)
        .flatMap(f -> VfsUtil.collectChildrenRecursively(f))
        .map(f -> VcsFileUtil.getRelativeFilePath(f, projectBasePath))
        .filter(Objects::nonNull)
        .map(p -> "/" + p);
      if (paths.isNotEmpty()) {
        processMethodsAsync(project, PsiMethod.EMPTY_ARRAY, paths.toList(), createTreeProcessor(tree), () -> tree.setPaintBusy(false));
      }
    });
  }

  @NotNull
  private static String createTitle(@NotNull List<? extends VirtualFile> files) {
    if (files.isEmpty()) return "Empty Selection";
    String firstName = files.get(0).getName();
    if (files.size() == 1) return firstName;
    if (files.size() == 2) return firstName + " and " + files.get(1).getName();
    return firstName + " et al.";
  }

  private static void showDiscoveredTestsByPsiClass(@NotNull Project project, @NotNull PsiClass psiClass, @NotNull AnActionEvent e) {
    if (DumbService.isDumb(project)) return;
    DataContext dataContext = DataManager.getInstance().getDataContext(e.getRequiredData(EDITOR).getContentComponent());
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery");
    String presentableName = PsiFormatUtil.formatClass(psiClass, PsiFormatUtilBase.SHOW_NAME);
    DiscoveredTestsTree tree = showTree(project, dataContext, presentableName, e.getPlace());
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (DumbService.isDumb(project)) return;
      String className = ReadAction.compute(() -> DiscoveredTestsTreeModel.getClassName(psiClass));
      if (className == null) return;
      List<Couple<String>> classesAndMethods = new SmartList<>(Couple.of(className, null));
      processTestDiscovery(project, createTreeProcessor(tree), classesAndMethods, Collections.emptyList());
      EdtInvocationManager.getInstance().invokeLater(() -> tree.setPaintBusy(false));
    });
  }

  private static void showDiscoveredTestsByPsiMethod(@NotNull Project project, @NotNull PsiMethod method, @NotNull AnActionEvent e) {
    Couple<String> key = getMethodKey(method);
    if (key == null) return;
    DataContext dataContext = DataManager.getInstance().getDataContext(e.getRequiredData(EDITOR).getContentComponent());
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery");
    String presentableName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, 0);
    DiscoveredTestsTree tree = showTree(project, dataContext, presentableName, e.getPlace());
    processMethodsAsync(project, new PsiMethod[]{method}, Collections.emptyList(), createTreeProcessor(tree), () -> tree.setPaintBusy(false));
  }

  @NotNull
  private static TestDiscoveryProducer.PsiTestProcessor createTreeProcessor(@NotNull DiscoveredTestsTree tree) {
    return (clazz, method, parameter) -> {
      tree.addTest(clazz, method, parameter);
      return true;
    };
  }

  private static void showDiscoveredTestsByChanges(@NotNull AnActionEvent e) {
    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    Project project = e.getProject();
    assert project != null;
    showDiscoveredTestsByChanges(project, changes, "Selected Changes", e.getDataContext());
  }

  public static void showDiscoveredTestsByChanges(@NotNull Project project,
                                                  Change @NotNull [] changes,
                                                  @NotNull String title,
                                                  @NotNull DataContext dataContext) {
    DiscoveredTestsTree tree = showTree(project, dataContext, title, ActionPlaces.UNKNOWN);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery.selected.changes");

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      PsiMethod[] methods = findMethods(project, changes);
      List<String> filePaths = getRelativeAffectedPaths(project, Arrays.asList(changes));
      processMethodsAsync(project, methods, filePaths, createTreeProcessor(tree), () -> tree.setPaintBusy(false));
    });
  }

  public static PsiMethod @NotNull [] findMethods(@NotNull Project project, Change @NotNull ... changes) {
    UastMetaLanguage jvmLanguage = Language.findInstance(UastMetaLanguage.class);

    return PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
      return findChangedMethods(project, jvmLanguage, changes)
        .map(m -> UastContextKt.toUElement(m))
        .filter(Objects::nonNull)
        .map(m -> ObjectUtils.tryCast(m.getJavaPsi(), PsiMethod.class))
        .filter(m -> Objects.nonNull(m) && !ContainerUtil.exists(m.getParameterList().getParameters(), p -> p.getType() ==
                                                                                                            PsiTypes.nullType()))
        .toArray(PsiMethod.ARRAY_FACTORY::create);
    });
  }

  @NotNull
  private static Stream<PsiElement> findChangedMethods(@NotNull Project project,
                                                       @NotNull UastMetaLanguage jvmLanguage,
                                                       Change @NotNull [] changes) {
    return Arrays.stream(changes).flatMap(change -> {
      return VcsFacadeImpl.getVcsInstance().getLocalChangedElements(project, change, file -> {
          return getMethodsFromFile(project, jvmLanguage, file);
        })
        .stream();
    });
  }

  @Nullable
  private static List<PsiElement> getMethodsFromFile(@NotNull Project project,
                                                     @NotNull UastMetaLanguage jvmLanguage,
                                                     @NotNull VirtualFile file) {
    if (DumbService.isDumb(project) || project.isDisposed() || !file.isValid()) return null;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    if (!index.isInSource(file)) return null;
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null || !jvmLanguage.matchesLanguage(psiFile.getLanguage())) return null;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return null;

    List<PsiElement> physicalMethods = new SmartList<>();
    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        UMethod method = UastContextKt.toUElement(element, UMethod.class);
        if (method != null) {
          ContainerUtil.addAllNotNull(physicalMethods, method.getSourcePsi());
        }
        super.visitElement(element);
      }
    });
    return physicalMethods;
  }

  public static boolean isEnabled(@Nullable Project project) {
    return project != null &&
           (Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY) || ApplicationManager.getApplication().isInternal());
  }

  @NotNull
  private static List<VirtualFile> findFilesInContext(@NotNull AnActionEvent event) {
    VirtualFile[] virtualFiles = event.getData(VIRTUAL_FILE_ARRAY);
    if (virtualFiles == null || virtualFiles.length == 0) {
      PsiFile file = event.getData(PSI_FILE);
      if (file != null) {
        virtualFiles = new VirtualFile[]{file.getVirtualFile()};
      }
    }
    return virtualFiles == null
           ? Collections.emptyList()
           : ContainerUtil.filter(virtualFiles, v -> v.isInLocalFileSystem());
  }

  @Nullable
  private static PsiMethod findMethodAtCaret(@NotNull AnActionEvent e) {
    UMethod uMethod = UastUtils.findContaining(findElementAtCaret(e), UMethod.class);
    return uMethod == null ? null : uMethod.getJavaPsi();
  }

  @Nullable
  private static PsiClass findClassAtCaret(@NotNull AnActionEvent e) {
    UClass uClass = UastUtils.findContaining(findElementAtCaret(e), UClass.class);
    return uClass == null ? null : uClass.getJavaPsi();
  }

  @Nullable
  private static PsiElement findElementAtCaret(@NotNull AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    if (editor == null || file == null) return null;
    int offset = editor.getCaretModel().getOffset();
    PsiElement at = file.findElementAt(offset);
    if (at instanceof PsiWhiteSpace && offset > 0) {
      PsiElement prev = file.findElementAt(offset - 1);
      if (!(prev instanceof PsiWhiteSpace)) return prev;
    }
    return at;
  }

  @NotNull
  private static DiscoveredTestsTree showTree(@NotNull Project project,
                                              @NotNull DataContext dataContext,
                                              @NotNull String title,
                                              @Nullable String place) {
    DiscoveredTestsTree tree = new DiscoveredTestsTree(title);
    String initTitle = JavaCompilerBundle.message("test.discovery.tests.tab.title", title);

    Ref<JBPopup> ref = new Ref<>();

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, place);

    ActiveComponent runButton =
      createButton(JavaCompilerBundle.message("action.run.all.affected.tests.text"), AllIcons.Actions.Execute, () -> runAllDiscoveredTests(project, tree, ref, context, initTitle), tree);

    Runnable pinActionListener = () -> {
      UsageView view = FindUtil.showInUsageView(null, tree.getTestMethods(), param -> param, initTitle, p -> {
        p.setCodeUsages(false); // don't show r/w, imports filtering actions
        p.setMergeDupLinesAvailable(false);
        p.setUsageTypeFilteringAvailable(false);
        p.setExcludeAvailable(false);
      }, project);
      if (view != null) {
        view.addButtonToLowerPane(new AbstractAction(JavaCompilerBundle.message("action.run.all.affected.tests.text"), AllIcons.Actions.Execute) {
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
      IdeBundle.message("show.in.find.window.button.name") +
      (findUsageKeyStroke == null ? "" : " " + KeymapUtil.getKeystrokeText(findUsageKeyStroke));
    ActiveComponent pinButton = createButton(pinTooltip, AllIcons.General.Pin_tab, pinActionListener, tree);

    PopupChooserBuilder<?> builder =
      new PopupChooserBuilder(tree)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(new CompositeActiveComponent(pinButton))
        .setSettingButton(new CompositeActiveComponent(runButton).getComponent())
        .setItemChosenCallback(() -> PsiNavigateUtil.navigate(tree.getSelectedElement()))
        .registerKeyboardAction(findUsageKeyStroke, __ -> pinActionListener.run())
        .setMinSize(new JBDimension(500, 300))
        .setDimensionServiceKey(ShowAffectedTestsAction.class.getSimpleName());

    JBPopup popup = builder.createPopup();
    ref.set(popup);

    TreeModel model = tree.getModel();
    Disposer.register(popup, tree);

    model.addTreeModelListener(new TreeModelAdapter() {
      @Override
      protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
        int testsCount = tree.getTestCount();
        int classesCount = tree.getTestClassesCount();
        popup.setCaption(JavaCompilerBundle.message("popup.title.affected.tests.counts", testsCount, testsCount == 1 ? 0 : 1, classesCount, classesCount == 1 ? 0 : 1, title));
      }
    });

    popup.showInBestPositionFor(dataContext);
    return tree;
  }

  public static void processMethodsAsync(@NotNull Project project,
                                         PsiMethod @NotNull [] methods,
                                         @NotNull List<String> filePaths,
                                         @NotNull TestDiscoveryProducer.PsiTestProcessor processor,
                                         @Nullable Runnable doWhenDone) {
    if (DumbService.isDumb(project)) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      processMethods(project, methods, filePaths, processor);
      if (doWhenDone != null) {
        EdtInvocationManager.getInstance().invokeLater(doWhenDone);
      }
    });
  }

  public static void processMethods(@NotNull Project project,
                                    PsiMethod @NotNull [] methods,
                                    @NotNull List<String> filePaths,
                                    @NotNull TestDiscoveryProducer.PsiTestProcessor processor) {
    List<Couple<String>> classesAndMethods =
      ReadAction.nonBlocking(() -> Arrays.stream(methods)
      .map(method -> getMethodKey(method)).filter(Objects::nonNull).collect(Collectors.toList())).executeSynchronously();
    processTestDiscovery(project, processor, classesAndMethods, filePaths);
  }

  private static void processTestDiscovery(@NotNull Project project,
                                           @NotNull TestDiscoveryProducer.PsiTestProcessor processor,
                                           @NotNull List<Couple<String>> classesAndMethods,
                                           @NotNull List<String> filePaths) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    for (TestDiscoveryConfigurationProducer producer : getRunConfigurationProducers(project)) {
      byte frameworkId =
        ((JavaTestConfigurationWithDiscoverySupport)producer.getConfigurationFactory().createTemplateConfiguration(project))
          .getTestFrameworkId();
      TestDiscoveryProducer.consumeDiscoveredTests(project, classesAndMethods, frameworkId, filePaths, (testClass, testMethod, parameter) -> {
        PsiClass[] testClassPsi = {null};
        PsiMethod[] testMethodPsi = {null};
        ReadAction.run(() -> DumbService.getInstance(project).runWithAlternativeResolveEnabled(() -> {
          testClassPsi[0] = ClassUtil.findPsiClass(PsiManager.getInstance(project), testClass, null, true, scope);
          boolean checkBases = parameter != null; // check bases for parameterized tests
          if (testClassPsi[0] != null) {
            testMethodPsi[0] = ArrayUtil.getFirstElement(testClassPsi[0].findMethodsByName(testMethod, checkBases));
          }
        }));
        if (testClassPsi[0] != null) {
          if (!processor.process(testClassPsi[0], testMethodPsi[0], parameter)) return false;
        }
        return true;
      });
    }
  }

  @NotNull
  private static ActiveComponent createButton(@NotNull @NlsActions.ActionText String text,
                                              @NotNull Icon icon,
                                              @NotNull Runnable listener,
                                              @NotNull DiscoveredTestsTree tree) {
    return new ActiveComponent.Adapter() {
      @NotNull
      @Override
      public JComponent getComponent() {
        Presentation presentation = new Presentation();
        presentation.setText(text);
        presentation.setDescription(text);
        presentation.setIcon(icon);

        presentation.setEnabled(false);
        tree.getModel().addTreeModelListener(new TreeModelAdapter() {
          @Override
          protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
            if (!presentation.isEnabled() && tree.getTestCount() != 0) {
              presentation.setEnabled(true);
            }
          }
        });

        return new ActionButton(new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            listener.run();
          }
        }, presentation, "ShowDiscoveredTestsToolbar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      }
    };
  }

  private static void runAllDiscoveredTests(@NotNull Project project,
                                            @NotNull DiscoveredTestsTree tree,
                                            @NotNull Ref<? extends JBPopup> ref,
                                            @NotNull ConfigurationContext context,
                                            @NotNull String title) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Module targetModule = TestDiscoveryConfigurationProducer.detectTargetModule(tree.getContainingModules(), project);
    //first producer with results will be picked
    List<Location<PsiMethod>> testMethods = Arrays.stream(tree.getTestMethods())
      .map(TestMethodUsage::calculateLocation)
      .filter(Objects::nonNull)
      .toList();

    getRunConfigurationProducers(project).stream()
      .map(producer -> pair(producer, ContainerUtil.filter(testMethods, producer::isApplicable)))
      .max(Comparator.comparingInt(p -> p.second.size()))
      .map(p -> {
        @SuppressWarnings("unchecked") Location<PsiMethod>[] locations = p.second.toArray(new Location[0]);
        return p.first.createProfile(locations, targetModule, context, title);
      })
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
  public static Couple<String> getMethodKey(@NotNull PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) return null;
    PsiClass c = method.isValid() ? method.getContainingClass() : null;
    String fqn = c != null ? DiscoveredTestsTreeModel.getClassName(c) : null;
    return fqn == null ? null : Couple.of(fqn, methodSignature(method));
  }

  @NotNull
  private static String methodSignature(@NotNull PsiMethod method) {
    String tail = TestDiscoveryInstrumentationUtils.SEPARATOR + ClassUtil.getAsmMethodSignature(method);
    return (method.isConstructor() ? "<init>" : method.getName()) + tail;
  }

  @Nullable
  private static KeyStroke findUsagesKeyStroke() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
    ShortcutSet shortcutSet = action == null ? null : action.getShortcutSet();
    return shortcutSet == null ? null : KeymapUtil.getKeyStroke(shortcutSet);
  }

  @NotNull
  private static List<TestDiscoveryConfigurationProducer> getRunConfigurationProducers(@NotNull Project project) {
    return RunConfigurationProducer.getProducers(project)
      .stream()
      .filter(producer -> producer instanceof TestDiscoveryConfigurationProducer)
      .map(producer -> (TestDiscoveryConfigurationProducer)producer)
      .collect(Collectors.toList());
  }

  @NotNull
  public static List<String> getRelativeAffectedPaths(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    VirtualFile baseDir = getBasePathAsVirtualFile(project);
    return baseDir == null ?
           Collections.emptyList() :
           changes.stream()
             .map(change -> relativePath(baseDir, change))
             .filter(Objects::nonNull)
             .map(s -> "/" + s)
             .collect(Collectors.toList());
  }

  @Nullable
  static VirtualFile getBasePathAsVirtualFile(@NotNull Project project) {
    String basePath = project.getBasePath();
    return basePath == null ? null : LocalFileSystem.getInstance().findFileByPath(basePath);
  }

  @Nullable
  private static String relativePath(@NotNull VirtualFile baseDir, @NotNull Change change) {
    VirtualFile file = change.getVirtualFile();

    if (file == null) {
      ContentRevision before = change.getBeforeRevision();
      if (before != null) {
        return VcsFileUtil.relativePath(baseDir, before.getFile());
      }
    }

    return file == null ? null : VfsUtilCore.getRelativePath(file, baseDir);
  }
}
