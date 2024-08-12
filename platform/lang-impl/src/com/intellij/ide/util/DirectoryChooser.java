// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util;

import com.intellij.ide.actions.GotoClassPresentationUpdater;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DirectoryChooser extends DialogWrapper {
  private static final String FILTER_NON_EXISTING = "filter_non_existing";
  private static final String DEFAULT_SELECTION = "last_directory_selection";

  private final DirectoryChooserView myView;
  private boolean myShowExisting;
  private PsiDirectory myDefaultSelection;
  private final List<ItemWrapper> myItems = new ArrayList<>();
  private PsiElement mySelection;
  private final TabbedPaneWrapper myTabbedPaneWrapper;
  private final ChooseByNamePanel myByClassPanel;
  private final ChooseByNamePanel myByFilePanel;
  private final JLabel myDescription = new JLabel();

  public DirectoryChooser(@NotNull Project project){
    this(project, new DirectoryChooserModuleTreeView(project));
  }

  public DirectoryChooser(@NotNull Project project, @NotNull DirectoryChooserView view){
    super(project, true);
    myView = view;
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    myShowExisting = !propertiesComponent.isTrueValue(FILTER_NON_EXISTING);
    myTabbedPaneWrapper = new TabbedPaneWrapper(getDisposable());
    String gotoClassText = GotoClassPresentationUpdater.getTabTitle();
    myByClassPanel = gotoClassText.startsWith("Class") ? createChooserPanel(project, true) : null;
    myByFilePanel = createChooserPanel(project, false);
    init();
  }

  public void setDescription(@NlsContexts.Label String description) {
    myDescription.setText(description);
  }
  
  private ChooseByNamePanel createChooserPanel(@NotNull Project project, boolean useClass) {
    //@formatter:off
    ChooseByNameModel model =
      useClass ? new GotoClassModel2(project) {
        @Override public boolean loadInitialCheckBoxState() { return true; }
        @Override public void saveInitialCheckBoxState(boolean state) {}
        @Override public@Nullable  String getPromptText() { return null; }} :
      new GotoFileModel(project) {
        @Override public boolean loadInitialCheckBoxState() { return true; }
        @Override public void saveInitialCheckBoxState(boolean state) {} 
        @Override public String getPromptText() { return null; }};
    //@formatter:on
    ChooseByNamePanel panel = new ChooseByNamePanel(project, model, "", false, null) {
      @Override
      protected void showTextFieldPanel() {
      }

      @Override
      protected void close(boolean isOk) {
        super.close(isOk);
        if (isOk) {
          final List<Object> elements = getChosenElements();
          if (!elements.isEmpty()) {
            myActionListener.elementChosen(elements.get(0));
          }
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }
    };
    UiNotifyConnector.doWhenFirstShown(panel.getPanel(), () -> {
      panel.invoke(new ChooseByNamePopupComponent.Callback() {
        @Override
        public void elementChosen(Object element) {
          setSelection(element);
        }
      }, ModalityState.stateForComponent(getRootPane()), false);
    });
    Disposer.register(myDisposable, panel);
    return panel;
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(FILTER_NON_EXISTING, !myShowExisting);
    JComponent selectedTab = myTabbedPaneWrapper.getSelectedComponent();
    if (selectedTab == myByFilePanel.getPanel() ||
        myByClassPanel != null && selectedTab == myByClassPanel.getPanel()) {
      setSelection(selectedTab == myByFilePanel.getPanel()
                   ? myByFilePanel.getChosenElement()
                   : myByClassPanel.getChosenElement());
    }
    final ItemWrapper item = myView.getSelectedItem();
    if (item != null) {
      final PsiDirectory directory = item.getDirectory();
      if (directory != null) {
        PropertiesComponent.getInstance(directory.getProject()).setValue(DEFAULT_SELECTION, directory.getVirtualFile().getPath());
      }
    }
    super.doOKAction();
  }

  @Override
  protected @Nullable JComponent createNorthPanel() {
    return myDescription;
  }

  @Override
  protected JComponent createCenterPanel(){
    final JPanel panel = new JPanel(new BorderLayout());

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new FilterExistentAction());
    ActionToolbar chooser = ActionManager.getInstance().createActionToolbar("DirectoryChooser", actionGroup, true);
    chooser.setTargetComponent(myView.getComponent());
    JComponent chooserComponent = chooser.getComponent();
    chooserComponent.setBorder(JBUI.Borders.empty(6, 0));
    panel.add(chooserComponent, BorderLayout.NORTH);

    final Runnable runnable = () -> enableButtons();
    myView.onSelectionChange(runnable);
    final JComponent component = myView.getComponent();
    final JScrollPane jScrollPane = ScrollPaneFactory.createScrollPane(component);
    int prototypeWidth = component.getFontMetrics(component.getFont()).stringWidth("X:\\1234567890\\1234567890\\com\\company\\system\\subsystem");
    jScrollPane.setPreferredSize(new Dimension(Math.max(300, prototypeWidth),300));
    jScrollPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);


    installEnterAction(component);
    panel.add(jScrollPane, BorderLayout.CENTER);
    myTabbedPaneWrapper.addTab(LangBundle.message("tab.title.directory.structure"), panel);
    if (myByClassPanel != null) {
      myTabbedPaneWrapper.addTab(LangBundle.message("tab.title.by.class"), myByClassPanel.getPanel());
    }
    myTabbedPaneWrapper.addTab(LangBundle.message("tab.title.by.file"), myByFilePanel.getPanel());
    myTabbedPaneWrapper.addChangeListener(e -> enableButtons());
    return myTabbedPaneWrapper.getComponent();
  }

  private void setSelection(Object element) {
    if (element instanceof PsiElement) {
      mySelection = (PsiElement)element;
    }
  }

  private void installEnterAction(final JComponent component) {
    final KeyStroke enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0);
    final InputMap inputMap = component.getInputMap();
    final ActionMap actionMap = component.getActionMap();
    final Object oldActionKey = inputMap.get(enterKeyStroke);
    final Action oldAction = oldActionKey != null ? actionMap.get(oldActionKey) : null;
    inputMap.put(enterKeyStroke, "clickButton");
    actionMap.put("clickButton", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (isOKActionEnabled()) {
          doOKAction();
        }
        else if (oldAction != null) {
          oldAction.actionPerformed(e);
        }
      }
    });
  }

  @Override
  protected String getDimensionServiceKey() {
    return "chooseDestDirectoryDialog";
  }

  private void buildFragments() {
    ArrayList<String[]> paths = new ArrayList<>();
    for (int i = 0; i < myView.getItemsSize(); i++) {
      ItemWrapper item = myView.getItemByIndex(i);
      paths.add(ArrayUtilRt.toStringArray(FileUtil.splitPath(item.getPresentableUrl())));
    }
    FragmentBuilder headBuilder = new FragmentBuilder(paths){
        @Override
        protected void append(String fragment, StringBuffer buffer) {
          buffer.append(mySeparator);
          buffer.append(fragment);
        }

        @Override
        protected int getFragmentIndex(String[] path, int index) {
          return path.length > index ? index : -1;
        }
      };
    String commonHead = headBuilder.execute();
    final int headLimit = headBuilder.getIndex();
    FragmentBuilder tailBuilder = new FragmentBuilder(paths) {
        @Override
        protected void append(String fragment, StringBuffer buffer) {
          buffer.insert(0, fragment + mySeparator);
        }

        @Override
        protected int getFragmentIndex(String[] path, int index) {
          int result = path.length - 1 - index;
          return result > headLimit ? result : -1;
        }
      };
    String commonTail = tailBuilder.execute();
    int tailLimit = tailBuilder.getIndex();
    for (int i = 0; i < myView.getItemsSize(); i++) {
      ItemWrapper item = myView.getItemByIndex(i);
      String special = concat(paths.get(i), headLimit, tailLimit);
      item.setFragments(createFragments(commonHead, special, commonTail));
    }
  }

  private static @Nullable String concat(String[] strings, int headLimit, int tailLimit) {
    if (strings.length <= headLimit + tailLimit) return null;
    StringBuilder buffer = new StringBuilder();
    String separator = "";
    for (int i = headLimit; i < strings.length - tailLimit; i++) {
      buffer.append(separator);
      buffer.append(strings[i]);
      separator = File.separator;
    }
    return buffer.toString();
  }

  private static PathFragment[] createFragments(String head, String special, String tail) {
    ArrayList<PathFragment> list = new ArrayList<>(3);
    if (head != null) {
      if (special != null || tail != null) list.add(new PathFragment(head + File.separatorChar, true));
      else return new PathFragment[]{new PathFragment(head, true)};
    }
    if (special != null) {
      if (tail != null) list.add(new PathFragment(special + File.separatorChar, false));
      else list.add(new PathFragment(special, false));
    }
    if (tail != null) list.add(new PathFragment(tail, true));
    return list.toArray(new PathFragment[0]);
  }

  private abstract static class FragmentBuilder {
    private final ArrayList<String[]> myPaths;
    private final StringBuffer myBuffer = new StringBuffer();
    private int myIndex;
    protected String mySeparator = "";

    FragmentBuilder(ArrayList<String[]> pathes) {
      myPaths = pathes;
      myIndex = 0;
    }

    public int getIndex() { return myIndex; }

    public @Nullable String execute() {
      while (true) {
        String commonHead = getCommonFragment(myIndex);
        if (commonHead == null) break;
        append(commonHead, myBuffer);
        mySeparator = File.separator;
        myIndex++;
      }
      return myIndex > 0 ? myBuffer.toString() : null;
    }

    protected abstract void append(String fragment, StringBuffer buffer);

    private @Nullable String getCommonFragment(int count) {
      String commonFragment = null;
      for (String[] path : myPaths) {
        int index = getFragmentIndex(path, count);
        if (index == -1) return null;
        if (commonFragment == null) {
          commonFragment = path[index];
          continue;
        }
        if (!Comparing.strEqual(commonFragment, path[index], SystemInfo.isFileSystemCaseSensitive)) return null;
      }
      return commonFragment;
    }

    protected abstract int getFragmentIndex(String[] path, int index);
  }

  public static final class ItemWrapper {
    private final @Nullable PsiDirectory myDirectory;
    private final @Nullable Module myModule;
    private final @Nullable String myPostfix;
    private final @NotNull Icon myIcon;
    private final @NotNull String myRelativeToProjectPath;
    private PathFragment @Nullable [] myFragments;

    /**
     * Can be created outside BG thread.
     */
    public static final ItemWrapper NULL = new ItemWrapper(null, null);

    @RequiresBackgroundThread(generateAssertion = false)
    public ItemWrapper(@Nullable PsiDirectory directory, @Nullable String postfix) {
      myDirectory = directory;
      myPostfix = postfix != null && !postfix.isEmpty() ? postfix : null;
      myIcon = directory != null ? getIconInternal(directory) : PlatformIcons.FOLDER_ICON;
      VirtualFile virtualFile = directory != null ? directory.getVirtualFile() : null;
      Project project = directory != null ? directory.getProject() : null;
      myRelativeToProjectPath =
        virtualFile != null ? ProjectUtil.calcRelativeToProjectPath(virtualFile, directory.getProject(), true, false, true) +
                              ObjectUtils.notNull(myPostfix, "") : getPresentableUrl();
      myModule = virtualFile != null ? ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(virtualFile) : null;
    }

    public PathFragment[] getFragments() { return myFragments; }

    public void setFragments(PathFragment @Nullable [] fragments) {
      myFragments = fragments;
    }

    /**
     * @deprecated use {@link #getIcon()} directly
     */
    @Deprecated
    public Icon getIcon(@SuppressWarnings("unused") FileIndex fileIndex) {
      return getIcon();
    }

    public @NotNull Icon getIcon() {
      return myIcon;
    }

    private static Icon getIconInternal(@NotNull PsiDirectory directory) {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
      VirtualFile virtualFile = directory.getVirtualFile();
      if (fileIndex.isInTestSourceContent(virtualFile)) {
        return PlatformIcons.MODULES_TEST_SOURCE_FOLDER;
      }
      else if (fileIndex.isInSourceContent(virtualFile)) {
        return PlatformIcons.MODULES_SOURCE_FOLDERS_ICON;
      }
      else {
        return PlatformIcons.FOLDER_ICON;
      }
    }

    public String getPresentableUrl() {
      String directoryUrl;
      if (myDirectory != null) {
        directoryUrl = myDirectory.getVirtualFile().getPresentableUrl();
        final VirtualFile baseDir = ProjectUtil.guessProjectDir(myDirectory.getProject());
        if (baseDir != null) {
          final String projectHomeUrl = baseDir.getPresentableUrl();
          if (directoryUrl.startsWith(projectHomeUrl)) {
            directoryUrl = "..." + directoryUrl.substring(projectHomeUrl.length());
          }
        }
      }
      else {
        directoryUrl = "";
      }
      return myPostfix != null ? directoryUrl + myPostfix : directoryUrl;
    }

    public @Nullable PsiDirectory getDirectory() {
      return myDirectory;
    }

    public @Nullable String getPostfix() {
      return myPostfix;
    }

    public @NlsSafe @NotNull String getRelativeToProjectPath() {
      return myRelativeToProjectPath;
    }

    public @Nullable Module getModule() {
      return myModule;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return myView.getComponent();
  }

  public void fillList(
    PsiDirectory @NotNull [] directories,
    @Nullable PsiDirectory defaultSelection,
    @NotNull Project project,
    String postfixToShow
  ) {
    fillList(directories, defaultSelection, project, postfixToShow, null);
  }

  public void fillList(
    PsiDirectory @NotNull [] directories,
    @Nullable PsiDirectory defaultSelection,
    @NotNull Project project,
    @Nullable Map<PsiDirectory,String> postfixes
  ) {
    fillList(directories, defaultSelection, project, null, postfixes);
  }

  private void fillList(
    PsiDirectory @NotNull [] directories,
    @Nullable PsiDirectory defaultSelection,
    @NotNull Project project,
    @Nullable String postfixToShow,
    @Nullable Map<PsiDirectory,String> postfixes
  ) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.run(() -> fillItems(directories, postfixToShow, postfixes)),
      LangBundle.message("progress.title.validating"), true, project
    );
    if (defaultSelection == null) {
      defaultSelection = getDefaultSelection(directories, project);
      if (defaultSelection == null && directories.length > 0) {
        defaultSelection = directories[0];
      }
    }
    int selectionIndex = ArrayUtil.indexOf(directories, defaultSelection);
    if (selectionIndex < 0 && directories.length == 1) {
      selectionIndex = 0;
    }

    if (selectionIndex < 0) {
      // find source root corresponding to defaultSelection
      final PsiManager manager = PsiManager.getInstance(project);
      VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
      for (VirtualFile sourceRoot : sourceRoots) {
        if (sourceRoot.isDirectory()) {
          PsiDirectory directory = manager.findDirectory(sourceRoot);
          if (directory != null && isParent(defaultSelection, directory)) {
            defaultSelection = directory;
            break;
          }
        }
      }
    }

    updateView(defaultSelection, selectionIndex);
  }

  private void updateView(@Nullable PsiDirectory defaultSelection, int selectionIndex) {
    if (myView.getItemsSize() > 0){
      myView.clearItems();
    }
    int existingIdx = 0;
    for(int i = 0; i < myItems.size(); i++){
      ItemWrapper itemWrapper = myItems.get(i);
      PsiDirectory directory = itemWrapper.getDirectory();
      String postfixForDirectory = itemWrapper.myPostfix;
      if (myShowExisting) {
        if (selectionIndex == i) selectionIndex = -1;
        if (postfixForDirectory != null
            && directory != null
            && directory.getVirtualFile().findFileByRelativePath(StringUtil.trimStart(postfixForDirectory, File.separator)) == null
        ) {
          if (isParent(directory, defaultSelection)) {
            myDefaultSelection = directory;
          }
          continue;
        }
      }

      myView.addItem(itemWrapper);
      if (selectionIndex < 0 && isParent(directory, defaultSelection)) {
        selectionIndex = existingIdx;
      }
      existingIdx++;
    }
    buildFragments();
    myView.listFilled();
    if (myView.getItemsSize() > 0) {
      if (selectionIndex != -1) {
        myView.selectItemByIndex(selectionIndex);
      } else {
        myView.selectItemByIndex(0);
      }
    }
    else {
      myView.clearSelection();
    }
    enableButtons();
    myView.getComponent().repaint();
  }

  private void fillItems(PsiDirectory @NotNull [] directories,
                         @Nullable String postfixToShow,
                         @Nullable Map<PsiDirectory, String> postfixes) {
    for (PsiDirectory directory : directories) {
      ProgressManager.checkCanceled();
      final String postfixForDirectory;
      if (postfixes == null) {
        postfixForDirectory = postfixToShow;
      }
      else {
        postfixForDirectory = postfixes.get(directory);
      }
      myItems.add(new ItemWrapper(directory, postfixForDirectory));
    }
  }

  private static @Nullable PsiDirectory getDefaultSelection(PsiDirectory[] directories, Project project) {
    final String defaultSelectionPath = PropertiesComponent.getInstance(project).getValue(DEFAULT_SELECTION);
    if (defaultSelectionPath != null) {
      final VirtualFile directoryByDefault = LocalFileSystem.getInstance().findFileByPath(defaultSelectionPath);
      if (directoryByDefault != null) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(directoryByDefault);
        return directory != null && ArrayUtil.find(directories, directory) > -1 ? directory : null;
      }
    }
    return null;
  }

  private static boolean isParent(PsiDirectory directory, PsiDirectory parentCandidate) {
    while (directory != null) {
      if (directory.equals(parentCandidate)) return true;
      directory = directory.getParentDirectory();
    }
    return false;
  }

  private void enableButtons() {
    JComponent selectedTab = myTabbedPaneWrapper.getSelectedComponent();
    setOKActionEnabled(selectedTab != null && 
                       (selectedTab == myByFilePanel.getPanel() || myByClassPanel != null && selectedTab == myByClassPanel.getPanel() || myView.getSelectedItem() != null));
  }

  public @Nullable PsiDirectory getSelectedDirectory() {
    if (mySelection != null) {
      final PsiFile file = mySelection.getContainingFile();
      if (file != null){
        return file.getContainingDirectory();
      }
    }
    ItemWrapper wrapper = myView.getSelectedItem();
    if (wrapper == null) return null;
    return wrapper.myDirectory;
  }


  public static final class PathFragment {
    private final String myText;
    private final boolean myCommon;

    public PathFragment(String text, boolean isCommon) {
      myText = text;
      myCommon = isCommon;
    }

    public @NlsSafe String getText() {
      return myText;
    }

    public boolean isCommon() {
      return myCommon;
    }
  }


  private final class FilterExistentAction extends CheckboxAction {
    FilterExistentAction() {
      super(RefactoringBundle.messagePointer("directory.chooser.hide.non.existing.checkBox.text"),
            () -> UIUtil.removeMnemonic(RefactoringBundle.message("directory.chooser.hide.non.existing.checkBox.text")),
            null);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myShowExisting;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myShowExisting = state;
      final ItemWrapper selectedItem = myView.getSelectedItem();
      PsiDirectory directory = selectedItem != null ? selectedItem.getDirectory() : null;
      if (directory == null && myDefaultSelection != null) {
        directory = myDefaultSelection;
      }
      updateView(directory, -1);
    }
  }
}
