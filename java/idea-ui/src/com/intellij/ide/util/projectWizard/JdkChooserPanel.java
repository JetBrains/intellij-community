// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaHomeFinder;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.StatusText;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * @deprecated use {@link SdkPopupFactory} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public class JdkChooserPanel extends JPanel {
  private final @Nullable Project myProject;
  private final DefaultListModel<Sdk> myListModel;
  private final JBList<Sdk> myList;
  private final LoadingDecorator myLoadingDecorator;
  private Sdk myCurrentJdk;
  private SdkType[] myAllowedJdkTypes = null;

  public JdkChooserPanel(@Nullable final Project project) {
    super(new BorderLayout());
    myProject = project;
    myListModel = new DefaultListModel<>();
    myList = new JBList<>(myListModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Sdk> list, Sdk value, int index, boolean selected, boolean hasFocus) {
        OrderEntryAppearanceService.getInstance().forJdk(value, false, selected, true).customize(this);
      }
    });

    myList.addListSelectionListener(e -> myCurrentJdk = myList.getSelectedValue());
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (myProject == null) {
          editJdkTable();
          return true;
        }
        return false;
      }
    }.installOn(myList);

    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, myList.getVisibleRowCount() * myList.getFixedCellHeight());
        return size;
      }
    };
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    myLoadingDecorator = new LoadingDecorator(panel, project, 0, true);
    myLoadingDecorator.setLoadingText(JavaUiBundle.message("loading.text.looking.for.jdks"));
    add(myLoadingDecorator.getComponent(), BorderLayout.CENTER);
    if (myListModel.getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  /**
   * Sets the JDK types which may be shown in the panel.
   *
   * @param allowedJdkTypes the array of JDK types which may be shown, or null if all JDK types are allowed.
   */
  public void setAllowedJdkTypes(final SdkType @Nullable [] allowedJdkTypes) {
    myAllowedJdkTypes = allowedJdkTypes;
  }

  @Nullable
  public Sdk getChosenJdk() {
    return myCurrentJdk;
  }

  public Object @NotNull [] getAllJdks() {
    return myListModel.toArray();
  }

  public void editJdkTable() {
    ProjectJdksEditor editor = new ProjectJdksEditor(myList.getSelectedValue(),
                                                     myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject(),
                                                     myList);
    if (editor.showAndGet()) {
      Sdk selectedJdk = editor.getSelectedJdk();
      updateList(selectedJdk, null);
    }
  }

  public void updateList(final Sdk selectedJdk, final @Nullable SdkType type) {
    updateList(selectedJdk, type, null);
  }

  public void updateList(final Sdk selectedJdk, final @Nullable SdkType type, final Sdk @Nullable [] globalSdks) {
    final int[] selectedIndices = myList.getSelectedIndices();
    fillList(type, globalSdks);
    // restore selection
    if (selectedJdk != null) {
      IntList list = new IntArrayList();
      for (int i = 0; i < myListModel.size(); i++) {
        Sdk jdk = myListModel.getElementAt(i);
        if (Comparing.strEqual(jdk.getName(), selectedJdk.getName())){
          list.add(i);
        }
      }
      final int[] indicesToSelect = list.toIntArray();
      if (indicesToSelect.length > 0) {
        myList.setSelectedIndices(indicesToSelect);
      }
      else if (myList.getModel().getSize() > 0) {
        myList.setSelectedIndex(0);
      }
    }
    else {
      if (selectedIndices.length > 0) {
        myList.setSelectedIndices(selectedIndices);
      }
      else {
        myList.setSelectedIndex(0);
      }
    }

    myCurrentJdk = myList.getSelectedValue();
  }

  public JList<Sdk> getPreferredFocusedComponent() {
    return myList;
  }

  public void fillList(final @Nullable SdkType type, final Sdk @Nullable [] globalSdks) {
    final ArrayList<Sdk> knownJdks = new ArrayList<>();
    if (myProject == null || myProject.isDefault()) {
      final Sdk[] allJdks = globalSdks != null ? globalSdks : ProjectJdkTable.getInstance().getAllJdks();
      knownJdks.addAll(getCompatibleJdks(type, Arrays.asList(allJdks)));
    }
    else {
      final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(myProject).getProjectJdksModel();
      if (!projectJdksModel.isInitialized()){ //should be initialized
        projectJdksModel.reset(myProject);
      }
      final Collection<Sdk> collection = projectJdksModel.getProjectSdks().values();
      knownJdks.addAll(getCompatibleJdks(type, collection));
    }
    final ArrayList<Sdk> allJdks = new ArrayList<>(knownJdks);

    if (Registry.is("autodetect.all.jdks") && (type == null || type instanceof JavaSdkType)) {
      myList.getEmptyText().setText("");
      myLoadingDecorator.startLoading(false);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<String> suggestedPaths = JavaHomeFinder.suggestHomePaths();
        //remove all known path to avoid duplicates
        for (Sdk sdk : knownJdks) {
          String homePath = sdk.getHomePath();
          if (homePath != null) {
            suggestedPaths.remove(FileUtil.toSystemDependentName(homePath));
          }
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          for (String homePath : suggestedPaths) {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(homePath);
            if (virtualFile != null) {
              JavaSdk sdkType = JavaSdk.getInstance();
              JavaVersion version = JavaVersion.tryParse(sdkType.getVersionString(homePath));
              String suggestedName = version != null ? version.toString() : "";
              Sdk jdk = sdkType.createJdk(suggestedName, homePath, false);
              if (jdk instanceof ProjectJdkImpl) {
                ProjectJdkImpl tmp = SdkConfigurationUtil.createSdk(allJdks, virtualFile, sdkType, null, suggestedName);
                String improvedName = tmp.getName();
                ((ProjectJdkImpl)jdk).setName(improvedName);
              }
              allJdks.add(jdk);
            }
          }
          updateListModel(allJdks, knownJdks);
          myLoadingDecorator.stopLoading();
          myList.getEmptyText().setText(StatusText.getDefaultEmptyText());
        }, ModalityState.any());
      });
    } else {
      updateListModel(allJdks, knownJdks);
    }
  }

  private void updateListModel(ArrayList<? extends Sdk> allJdks, ArrayList<? extends Sdk> knownJdks) {
    Sdk oldSelection = myList.getSelectedValue();

    myListModel.clear();
    allJdks.sort((o1, o2) -> {
      boolean unknown1 = !knownJdks.contains(o1);
      boolean unknown2 = !knownJdks.contains(o2);
      if (unknown1 != unknown2) {
        return unknown1 ? 1 : -1;
      }
      String v1 = o1.getVersionString();
      String v2 = o2.getVersionString();
      if (v1 != null & v2 != null) {
        try {
          return -JavaVersion.parse(v1).compareTo(JavaVersion.parse(v2));
        }
        catch (IllegalArgumentException ignored) {
          //
        }
      }
      return -o1.getName().compareToIgnoreCase(o2.getName());
    });
    for (Sdk jdk : allJdks) {
      myListModel.addElement(jdk);
    }
    if (oldSelection != null) {
      ScrollingUtil.selectItem(myList, oldSelection);
    }
  }

  private List<Sdk> getCompatibleJdks(final @Nullable SdkType type, final Collection<? extends Sdk> collection) {
    final Set<Sdk> compatibleJdks = new HashSet<>();
    for (Sdk projectJdk : collection) {
      if (isCompatibleJdk(projectJdk, type)) {
        compatibleJdks.add(projectJdk);
      }
    }
    return new ArrayList<>(compatibleJdks);
  }

  private boolean isCompatibleJdk(final Sdk projectJdk, final @Nullable SdkType type) {
    if (type != null) {
      return projectJdk.getSdkType() == type;
    }
    if (myAllowedJdkTypes != null) {
      return ArrayUtil.indexOf(myAllowedJdkTypes, projectJdk.getSdkType()) >= 0;
    }
    return true;
  }

  public JComponent getDefaultFocusedComponent() {
    return myList;
  }

  public void selectJdk(@Nullable Sdk defaultJdk) {
    if (defaultJdk != null) {
      ScrollingUtil.selectItem(myList, defaultJdk);
    }
  }

  public void addSelectionListener(final ListSelectionListener listener) {
    myList.addListSelectionListener(listener);
  }

  @Nullable
  private static Sdk showDialog(final Project project, @NlsContexts.DialogTitle String title, final Component parent, Sdk jdkToSelect) {
    final JdkChooserPanel jdkChooserPanel = new JdkChooserPanel(project);
    jdkChooserPanel.fillList(null, null);
    final MyDialog dialog = jdkChooserPanel.new MyDialog(parent);
    if (title != null) {
      dialog.setTitle(title);
    }
    if (jdkToSelect != null) {
      jdkChooserPanel.selectJdk(jdkToSelect);
    }
    else {
      ScrollingUtil.ensureSelectionExists(jdkChooserPanel.myList);
    }
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        dialog.clickDefaultButton();
        return true;
      }
    }.installOn(jdkChooserPanel.myList);
    return dialog.showAndGet() ? jdkChooserPanel.getChosenJdk() : null;
  }

  /**
   * @deprecated Use {@link SdkPopupFactory}
   */
  @Nullable
  @Deprecated
  public static Sdk chooseAndSetJDK(@NotNull final Project project) {
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    final Sdk jdk = showDialog(project, JavaUiBundle.message("module.libraries.target.jdk.select.title"), WindowManagerEx.getInstanceEx().getFrame(project), projectJdk);
    String path = jdk != null ? jdk.getHomePath() : null;
    if (path == null) {
      return null;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectJdkTable table = ProjectJdkTable.getInstance();
      List<Sdk> sdks = table.getSdksOfType(jdk.getSdkType());
      if (ContainerUtil.find(sdks, sdk -> path.equals(sdk.getHomePath())) == null) {
        table.addJdk(jdk);//this jdk is unknown yet and so it has to be added to Platform-level table now
      }
      ProjectRootManager.getInstance(project).setProjectSdk(jdk);
    });
    return jdk;
  }

  public class MyDialog extends DialogWrapper implements ListSelectionListener {

    public MyDialog(Component parent) {
      super(parent, true);
      setTitle(JavaUiBundle.message("title.select.jdk"));
      init();
      myList.addListSelectionListener(this);
      updateOkButton();
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.util.projectWizard.JdkChooserPanel.MyDialog";
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      updateOkButton();
    }

    private void updateOkButton() {
      setOKActionEnabled(myList.getSelectedValue() != null);
    }

    @Override
    public void dispose() {
      myList.removeListSelectionListener(this);
      super.dispose();
    }

    @Override
    protected JComponent createCenterPanel() {
      return JdkChooserPanel.this;
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{new ConfigureAction(), getOKAction(), getCancelAction()};
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myList;
    }

    private final class ConfigureAction extends AbstractAction {
      ConfigureAction() {
        super(JavaUiBundle.message("button.configure.e"));
        putValue(Action.MNEMONIC_KEY, new Integer('E'));
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        editJdkTable();
      }
    }
  }
}