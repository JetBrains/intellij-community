/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class JdkChooserPanel extends JPanel {
  private JList myList = null;
  private DefaultListModel myListModel = null;
  private Sdk myCurrentJdk;
  private final Project myProject;
  private SdkType[] myAllowedJdkTypes = null;

  public JdkChooserPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    myListModel = new DefaultListModel();
    myList = new JBList(myListModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ProjectJdkListRenderer());
    //noinspection HardCodedStringLiteral
    myList.setPrototypeCellValue("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myCurrentJdk = (Sdk)myList.getSelectedValue();
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && myProject == null) {
          editJdkTable();
        }
      }
    });

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JBScrollPane(myList), BorderLayout.CENTER);
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

  public void editJdkTable() {
    ProjectJdksEditor editor = new ProjectJdksEditor((Sdk)myList.getSelectedValue(),
                                                     myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject(),
                                                     myList);
    editor.show();
    if (editor.isOK()) {
      Sdk selectedJdk = editor.getSelectedJdk();
      updateList(selectedJdk, null);
    }
  }

  public void updateList(final Sdk selectedJdk, final SdkType type) {
    final int[] selectedIndices = myList.getSelectedIndices();
    fillList(type);
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

  public void fillList(final SdkType type) {
    myListModel.clear();
    final Sdk[] jdks;
    if (myProject == null) {
      final Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
      jdks = getCompatibleJdks(type, Arrays.asList(allJdks));
    }
    else {
      final ProjectJdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(myProject).getProjectJdksModel();
      if (!projectJdksModel.isInitialized()){ //should be initialized
        projectJdksModel.reset(myProject);
      }
      final Collection<Sdk> collection = projectJdksModel.getProjectJdks().values();
      jdks = getCompatibleJdks(type, collection);
    }
    Arrays.sort(jdks, new Comparator<Sdk>() {
      public int compare(final Sdk o1, final Sdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (Sdk jdk : jdks) {
      myListModel.addElement(jdk);
    }
  }

  private Sdk[] getCompatibleJdks(final SdkType type, final Collection<Sdk> collection) {
    final Set<Sdk> compatibleJdks = new HashSet<Sdk>();
    for (Sdk projectJdk : collection) {
      if (isCompatibleJdk(projectJdk, type)) {
        compatibleJdks.add(projectJdk);
      }
    }
    return compatibleJdks.toArray(new Sdk[compatibleJdks.size()]);
  }

  private boolean isCompatibleJdk(final Sdk projectJdk, final SdkType type) {
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

  private static Sdk showDialog(final Project project, String title, final Component parent, Sdk jdkToSelect) {
    final JdkChooserPanel jdkChooserPanel = new JdkChooserPanel(project);
    jdkChooserPanel.fillList(null);
    final MyDialog dialog = jdkChooserPanel.new MyDialog(parent);
    if (title != null) {
      dialog.setTitle(title);
    }
    if (jdkToSelect != null) {
      jdkChooserPanel.selectJdk(jdkToSelect);
    }
    jdkChooserPanel.myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          dialog.clickDefaultButton();
        }
      }
    });
    dialog.show();
    return dialog.isOK() ? jdkChooserPanel.getChosenJdk() : null;
  }

  public static Sdk chooseAndSetJDK(final Project project) {
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    final Sdk jdk = showDialog(project, ProjectBundle.message("module.libraries.target.jdk.select.title"), WindowManagerEx.getInstanceEx().getFrame(project), projectJdk);
    if (jdk == null) {
      return null;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(project).setProjectJdk(jdk);
      }
    });
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
