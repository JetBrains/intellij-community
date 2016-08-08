/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ClickListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.*;

public class JdkChooserPanel extends JPanel {
  private JList myList = null;
  private DefaultListModel myListModel = null;
  private Sdk myCurrentJdk;
  @Nullable private final Project myProject;
  private SdkType[] myAllowedJdkTypes = null;

  public JdkChooserPanel(@Nullable final Project project) {
    super(new BorderLayout());
    myProject = project;
    myListModel = new DefaultListModel();
    myList = new JBList(myListModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ProjectJdkListRenderer());

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myCurrentJdk = (Sdk)myList.getSelectedValue();
      }
    });
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (myProject == null) {
          editJdkTable();
        }
        return true;
      }
    }.installOn(myList);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    add(panel, BorderLayout.CENTER);
    if (myListModel.getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  /**
   * Sets the JDK types which may be shown in the panel.
   *
   * @param allowedJdkTypes the array of JDK types which may be shown, or null if all JDK types are allowed.
   * @since 7.0.3
   */
  public void setAllowedJdkTypes(@Nullable final SdkType[] allowedJdkTypes) {
    myAllowedJdkTypes = allowedJdkTypes;
  }

  public Sdk getChosenJdk() {
    return myCurrentJdk;
  }

  public Object[] getAllJdks() {
    return myListModel.toArray();
  }

  public void editJdkTable() {
    ProjectJdksEditor editor = new ProjectJdksEditor((Sdk)myList.getSelectedValue(),
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

  public void updateList(final Sdk selectedJdk, final @Nullable SdkType type, final @Nullable Sdk[] globalSdks) {
    final int[] selectedIndices = myList.getSelectedIndices();
    fillList(type, globalSdks);
    // restore selection
    if (selectedJdk != null) {
      TIntArrayList list = new TIntArrayList();
      for (int i = 0; i < myListModel.size(); i++) {
        final Sdk jdk = (Sdk)myListModel.getElementAt(i);
        if (Comparing.strEqual(jdk.getName(), selectedJdk.getName())){
          list.add(i);
        }
      }
      final int[] indicesToSelect = list.toNativeArray();
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

    myCurrentJdk = (Sdk)myList.getSelectedValue();
  }

  public JList getPreferredFocusedComponent() {
    return myList;
  }

  public void fillList(final @Nullable SdkType type, final @Nullable Sdk[] globalSdks) {
    myListModel.clear();
    final Sdk[] jdks;
    if (myProject == null || myProject.isDefault()) {
      final Sdk[] allJdks = globalSdks != null ? globalSdks : ProjectJdkTable.getInstance().getAllJdks();
      jdks = getCompatibleJdks(type, Arrays.asList(allJdks));
    }
    else {
      final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(myProject).getProjectJdksModel();
      if (!projectJdksModel.isInitialized()){ //should be initialized
        projectJdksModel.reset(myProject);
      }
      final Collection<Sdk> collection = projectJdksModel.getProjectSdks().values();
      jdks = getCompatibleJdks(type, collection);
    }
    Arrays.sort(jdks, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
    for (Sdk jdk : jdks) {
      myListModel.addElement(jdk);
    }
  }

  private Sdk[] getCompatibleJdks(final @Nullable SdkType type, final Collection<Sdk> collection) {
    final Set<Sdk> compatibleJdks = new HashSet<>();
    for (Sdk projectJdk : collection) {
      if (isCompatibleJdk(projectJdk, type)) {
        compatibleJdks.add(projectJdk);
      }
    }
    return compatibleJdks.toArray(new Sdk[compatibleJdks.size()]);
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

  public void selectJdk(Sdk defaultJdk) {
    final int index = myListModel.indexOf(defaultJdk);
    if (index >= 0) {
      myList.setSelectedIndex(index);
    }
  }

  public void addSelectionListener(final ListSelectionListener listener) {
    myList.addListSelectionListener(listener);
  }

  private static Sdk showDialog(final Project project, String title, final Component parent, Sdk jdkToSelect) {
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
      protected boolean onDoubleClick(MouseEvent e) {
        dialog.clickDefaultButton();
        return true;
      }
    }.installOn(jdkChooserPanel.myList);
    return dialog.showAndGet() ? jdkChooserPanel.getChosenJdk() : null;
  }

  public static Sdk chooseAndSetJDK(final Project project) {
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    final Sdk jdk = showDialog(project, ProjectBundle.message("module.libraries.target.jdk.select.title"), WindowManagerEx.getInstanceEx().getFrame(project), projectJdk);
    if (jdk == null) {
      return null;
    }
    ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(project).setProjectSdk(jdk));
    return jdk;
  }

  public class MyDialog extends DialogWrapper implements ListSelectionListener {

    public MyDialog(Component parent) {
      super(parent, true);
      setTitle(IdeBundle.message("title.select.jdk"));
      init();
      myList.addListSelectionListener(this);
      updateOkButton();
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.util.projectWizard.JdkChooserPanel.MyDialog";
    }

    public void valueChanged(ListSelectionEvent e) {
      updateOkButton();
    }

    private void updateOkButton() {
      setOKActionEnabled(myList.getSelectedValue() != null);
    }

    public void dispose() {
      myList.removeListSelectionListener(this);
      super.dispose();
    }

    protected JComponent createCenterPanel() {
      return JdkChooserPanel.this;
    }

    @NotNull
    protected Action[] createActions() {
      return new Action[]{new ConfigureAction(), getOKAction(), getCancelAction()};
    }

    public JComponent getPreferredFocusedComponent() {
      return myList;
    }

    private final class ConfigureAction extends AbstractAction {
      public ConfigureAction() {
        super(IdeBundle.message("button.configure.e"));
        putValue(Action.MNEMONIC_KEY, new Integer('E'));
      }

      public void actionPerformed(ActionEvent e) {
        editJdkTable();
      }
    }
  }


}
