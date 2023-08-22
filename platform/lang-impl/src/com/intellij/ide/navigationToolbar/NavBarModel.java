// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.intellij.psi.util.PsiUtilCore.findFileSystemItem;
import static com.intellij.util.concurrency.SequentialTaskExecutor.createSequentialApplicationPoolExecutor;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public class NavBarModel {

  private static final ExecutorService ourExecutor = createSequentialApplicationPoolExecutor("Navbar model builder");

  private final NavBarModelListener myNotificator;
  private final NavBarModelBuilder myBuilder;
  private final Project myProject;

  private volatile int mySelectedIndex;
  private volatile List<Object> myModel;

  private volatile boolean myChanged = true;
  private volatile boolean updated = false;
  private volatile boolean isFixedComponent = false;

  public NavBarModel(@NotNull Project project) {
    this(project, project.getMessageBus().syncPublisher(NavBarModelListener.NAV_BAR), NavBarModelBuilder.getInstance());
  }

  protected NavBarModel(Project project, NavBarModelListener notificator, NavBarModelBuilder builder) {
    myProject = project;
    myNotificator = notificator;
    myBuilder = builder;
    myModel = Collections.singletonList(myProject);
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  @Nullable Object getRawSelectedObject() {
    List<Object> model = myModel;
    if (model.isEmpty()) return null;
    int index = mySelectedIndex;
    int adjusted = index >= 0 && index < model.size()
                   ? index
                   : model.size() - 1;
    return model.get(adjusted);
  }

  @Nullable
  public Object getElement(int index) {
    Object raw = getRawElement(index);
    if (raw == null) return null;
    return unwrapRaw(raw);
  }

  @Nullable
  public Object getRawElement(int index) {
    List<Object> model = myModel;
    if (index != -1 && index < model.size()) {
      return model.get(index);
    }
    return null;
  }

  public int size() {
    return myModel.size();
  }

  public boolean isEmpty() {
    return myModel.isEmpty();
  }

  public int getIndexByModel(int index) {
    List<Object> model = myModel;
    if (index < 0) return model.size() + index;
    if (index >= model.size() && model.size() > 0) return index % model.size();
    return index;
  }

  public void updateModelAsync(@NotNull DataContext dataContext, @Nullable Runnable callback) {
    if (LaterInvocator.isInModalContext() ||
        PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) instanceof NavBarPanel) {
      return;
    }

    DataContext wrappedDataContext = wrapDataContext(dataContext);
    ReadAction.nonBlocking(() -> createModel(wrappedDataContext))
      .expireWith(myProject)
      .finishOnUiThread(ModalityState.current(), model -> {
        setModelWithUpdate(model);
        if (callback != null) callback.run();
      })
      .submit(ourExecutor);
  }

  @NotNull
  private static DataContext wrapDataContext(@NotNull DataContext context) {
    DataContext wrapped = Utils.wrapDataContext(context);
    if (Utils.isAsyncDataContext(wrapped)) return wrapped;

    return SimpleDataContext.builder().addAll(
      context,
      CommonDataKeys.PSI_FILE,
      CommonDataKeys.PROJECT,
      CommonDataKeys.VIRTUAL_FILE,
      PlatformCoreDataKeys.MODULE,
      CommonDataKeys.EDITOR,
      PlatformCoreDataKeys.SELECTED_ITEMS).build();
  }

  private void setModelWithUpdate(@Nullable List<Object> model) {
    if (model != null) setModel(model);

    setChanged(false);
    updated = true;
  }

  public void updateModel(DataContext dataContext) {
    setModelWithUpdate(createModel(dataContext));
  }

  @Nullable
  private List<Object> createModel(@NotNull DataContext dataContext) {
    if (updated && !isFixedComponent) return null;

    NavBarModelExtension ownerExtension = null;
    PsiElement psiElement = null;
    for (NavBarModelExtension extension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      psiElement = extension.getLeafElement(dataContext);
      if (psiElement != null) {
        ownerExtension = extension;
        break;
      }
    }

    if (psiElement == null) {
      psiElement = CommonDataKeys.PSI_FILE.getData(dataContext);
    }
    if (psiElement == null) {
      psiElement = findFileSystemItem(
        CommonDataKeys.PROJECT.getData(dataContext),
        CommonDataKeys.VIRTUAL_FILE.getData(dataContext));
    }

    if (ownerExtension == null) {
      psiElement = normalize(psiElement);
    }

    // Save to a local variable in order to avoid OutOfBounds exception while working with a volatile property
    final List<Object> model = myModel;
    if (!model.isEmpty() && Objects.equals(get(model, model.size() - 1), psiElement) && !myChanged) return null;

    if (psiElement != null && psiElement.isValid()) {
      return createModel(psiElement, dataContext, ownerExtension);
    }
    else {
      if (UISettings.getInstance().getShowNavigationBar() && !model.isEmpty()) return null;

      Object root = calculateRoot(dataContext);

      if (root != null) {
        return Collections.singletonList(root);
      }
    }

    return null;
  }

  private Object calculateRoot(DataContext dataContext) {
    // Narrow down the root element to the first interesting one
    Module root = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (root != null && !ModuleType.isInternal(root)) return root;

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    Object projectChild;
    Object projectGrandChild = null;

    CommonProcessors.FindFirstAndOnlyProcessor<Object> processor = new CommonProcessors.FindFirstAndOnlyProcessor<>();
    processChildren(project, processor);
    projectChild = processor.reset();
    if (projectChild != null) {
      processChildren(projectChild, processor);
      projectGrandChild = processor.reset();
    }
    return ObjectUtils.chooseNotNull(projectGrandChild, ObjectUtils.chooseNotNull(projectChild, project));
  }

  protected void updateModel(final PsiElement psiElement, @Nullable NavBarModelExtension ownerExtension) {
    setModel(createModel(psiElement, null, ownerExtension));
  }

  @NotNull
  private List<Object> createModel(final PsiElement psiElement, @Nullable DataContext dataContext, @Nullable NavBarModelExtension ownerExtension) {
    final Set<VirtualFile> roots = new HashSet<>();
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
    final ProjectFileIndex projectFileIndex = projectRootManager.getFileIndex();

    for (VirtualFile root : projectRootManager.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (parent == null || !projectFileIndex.isInContent(parent)) {
        roots.add(root);
      }
    }

    for (final NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      for (VirtualFile root : modelExtension.additionalRoots(psiElement.getProject())) {
        VirtualFile parent = root.getParent();
        if (parent == null || !projectFileIndex.isInContent(parent)) {
          roots.add(root);
        }
      }
    }

    List<Object> updatedModel =
      ReadAction.compute(() -> isValid(psiElement) ? myBuilder.createModel(psiElement, roots, dataContext, ownerExtension) : Collections.emptyList());

    return ContainerUtil.reverse(updatedModel);
  }

  void revalidate() {
    final List<Object> objects = new ArrayList<>();
    boolean update = false;
    for (Object o : myModel) {
      if (isValid(unwrapRaw(o))) {
        objects.add(o);
      }
      else {
        update = true;
        break;
      }
    }
    if (update) {
      setModel(objects);
    }
  }

  protected void setModel(List<Object> model) {
    setModel(model, false);
  }

  protected void setModel(List<Object> model, boolean force) {
    if (!model.equals(TreeAnchorizer.retrieveList(myModel))) {
      myModel = anchorizeList(model);
      myNotificator.modelChanged();

      mySelectedIndex = myModel.size() - 1;
      myNotificator.selectionChanged();
    }
    else if (force) {
      myModel = anchorizeList(model);
      myNotificator.modelChanged();
    }
  }

  private @NotNull List<Object> anchorizeList(@NotNull List<Object> model) {
    List<Object> list = TreeAnchorizer.anchorizeList(model);
    return !list.isEmpty() ? list : Collections.singletonList(myProject);
  }

  public void updateModel(final Object object) {
    if (object instanceof PsiElement) {
      updateModel((PsiElement)object, null);
    }
    else if (object instanceof Module) {
      List<Object> l = new ArrayList<>();
      l.add(myProject);
      l.add(object);
      setModel(l);
    }
  }

  protected boolean hasChildren(Object object) {
    return !processChildren(object, new CommonProcessors.FindFirstProcessor<>());
  }

  //to avoid the following situation: element was taken from NavBarPanel via data context and all left children
  // were truncated by traverseToRoot
  public void setChanged(boolean changed) {
    myChanged = changed;
  }

  static boolean isValid(final Object object) {
    if (object instanceof Project) {
      return !((Project)object).isDisposed();
    }
    if (object instanceof Module) {
      return !((Module)object).isDisposed();
    }
    if (object instanceof PsiElement) {
      return ReadAction.compute(() -> ((PsiElement)object).isValid()).booleanValue();
    }
    return object != null;
  }

  @Nullable
  public static PsiElement normalize(@Nullable PsiElement child) {
    if (child == null) return null;

    List<NavBarModelExtension> extensions = NavBarModelExtension.EP_NAME.getExtensionList();
    for (int i = extensions.size() - 1; i >= 0; i--) {
      NavBarModelExtension modelExtension = extensions.get(i);
      child = modelExtension.adjustElement(child);
      if (child == null) return null;
    }
    return child;
  }

  public List<Object> getChildren(final Object object) {
    final List<Object> result = new ArrayList<>();
    PairProcessor<Object, NavBarModelExtension> processor = (o, ext) -> {
      ContainerUtil.addIfNotNull(result, o instanceof PsiElement && ext.normalizeChildren() ? normalize((PsiElement)o) : o);
      return true;
    };

    processChildrenWithExtensions(object, processor);

    result.sort(new SiblingsComparator());
    return result;
  }

  private boolean processChildren(Object object, @NotNull Processor<Object> processor) {
    return processChildrenWithExtensions(object, (o, ext) -> processor.process(o));
  }

  private boolean processChildrenWithExtensions(Object object, @NotNull PairProcessor<Object, ? super NavBarModelExtension> pairProcessor) {
    if (!isValid(object)) return true;
    final Object rootElement = size() > 1 ? getElement(1) : null;
    if (rootElement != null && !isValid(rootElement)) return true;

    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      if (!modelExtension.processChildren(object, rootElement, o -> pairProcessor.process(o, modelExtension))) return false;
    }
    return true;
  }

  @Nullable Object unwrapRaw(@NotNull Object o) {
    return TreeAnchorizer.getService().retrieveElement(o);
  }

  public @Nullable Object get(int index) {
    return get(myModel, index);
  }

  private @Nullable Object get(List<Object> model, int index) {
    return unwrapRaw(model.get(index));
  }

  public int indexOf(Object value) {
    List<Object> model = myModel;
    for (int i = 0; i < model.size(); i++) {
      Object o = model.get(i);
      if (Objects.equals(unwrapRaw(o), value)) {
        return i;
      }
    }
    return -1;
  }

  public void setSelectedIndex(final int selectedIndex) {
    if (mySelectedIndex != selectedIndex) {
      mySelectedIndex = selectedIndex;
      myNotificator.selectionChanged();
    }
  }

  public void setFixedComponent(boolean fixedComponent) {
    isFixedComponent = fixedComponent;
  }

  private static final class SiblingsComparator implements Comparator<Object> {
    @Override
    public int compare(Object o1, Object o2) {
      int w1 = getWeight(o1);
      int w2 = getWeight(o2);
      if (w1 == 0) return w2 == 0 ? 0 : -1;
      if (w2 == 0) return 1;
      if (w1 != w2) return -w1 + w2;
      String s1 = NavBarPresentation.calcPresentableText(o1, false);
      String s2 = NavBarPresentation.calcPresentableText(o2, false);
      return StringUtil.naturalCompare(s1, s2);
    }

    private static int getWeight(Object object) {
      return object instanceof Module ? 5 :
             object instanceof PsiDirectoryContainer ? 4 :
             object instanceof PsiDirectory ? 4 :
             object instanceof PsiFile ? 2 :
             object instanceof PsiNamedElement ? 3 : 0;
    }
  }
}