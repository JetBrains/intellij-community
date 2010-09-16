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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public abstract class BaseLibrariesConfigurable extends BaseStructureConfigurable  {
  protected String myLevel;

  protected BaseLibrariesConfigurable(final Project project) {
    super(project);
  }


  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.library";
  }

  public boolean isModified() {
    boolean isModified = false;
    for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
      isModified |= provider.isChanged();
    }

    return isModified;
  }

  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  protected void loadTree() {
    createLibrariesNode(myContext.createModifiableModelProvider(myLevel));
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<ProjectStructureElement>();
    //todo[nik] improve
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final TreeNode node = myRoot.getChildAt(i);
      if (node instanceof MyNode) {
        final NamedConfigurable configurable = ((MyNode)node).getConfigurable();
        if (configurable instanceof LibraryConfigurable) {
          result.add(new LibraryProjectStructureElement(myContext, ((LibraryConfigurable)configurable).getEditableObject()));
        }
      }
    }
    return result;
  }

  private void createLibrariesNode(final StructureLibraryTableModifiableModelProvider modelProvider) {
    final Library[] libraries = modelProvider.getModifiableModel().getLibraries();
    for (Library library : libraries) {
      myRoot.add(new MyNode(new LibraryConfigurable(modelProvider, library, myProject, TREE_UPDATER)));
    }
    TreeUtil.sort(myRoot, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        MyNode node1 = (MyNode)o1;
        MyNode node2 = (MyNode)o2;
        return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
      }
    });
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
  }

  public void apply() throws ConfigurationException {
    super.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
          provider.deferredCommit();
        }
      }
    });
  }

  public MyNode createLibraryNode(Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final String level = table.getTableLevel();
      final LibraryConfigurable configurable =
        new LibraryConfigurable(myContext.createModifiableModelProvider(level), library, myProject, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, myRoot);
      myContext.getDaemonAnalyzer().queueUpdate(new LibraryProjectStructureElement(myContext, library));
      return node;
    }

    return null;
  }

  public void dispose() {
    for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
      provider.disposeUncommittedLibraries();
    }
  }

  protected AnAction createCopyAction() {
    return new MyCopyAction();
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(getAddText()) {
      {
        setPopup(false);
      }

      @NotNull
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return new AnAction[]{new CreateNewLibraryAction(getAddText(), getModelProvider(), myProject)};
      }
    };
  }

  protected abstract String getAddText();

  public abstract StructureLibraryTableModifiableModelProvider getModelProvider();

  public abstract BaseLibrariesConfigurable getOppositeGroup();

  protected void removeLibrary(final Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      getModelProvider().getModifiableModel().removeLibrary(library);
      myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
      // TODO: myContext.invalidateModules(myContext.myLibraryDependencyCache.get(library.getName()));
    }
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a library to view or edit its details here";
  }

  private class MyCopyAction extends AnAction {
    private JCheckBox mySaveAsCb;
    private JTextField myNameTf;
    private TextFieldWithBrowseButton myPathTf;
    private JPanel myWholePanel;

    private MyCopyAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final Object o = getSelectedObject();
      if (o instanceof LibraryImpl) {
        myPathTf.addBrowseFolderListener("Choose directory",
                                         ProjectBundle.message("directory.roots.copy.label"),
                                         myProject, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
        mySaveAsCb.addActionListener(new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            myPathTf.setEnabled(mySaveAsCb.isSelected());
          }
        });
        mySaveAsCb.setText(ProjectBundle.message("save.as.library.checkbox", getOppositeGroup().myLevel));
        mySaveAsCb.setSelected(false);
        myPathTf.setEnabled(false);

        final DialogWrapper dlg = new DialogWrapper(myTree, false) {
          {
            setTitle("Copy");
            init();
          }

          @Nullable
          protected JComponent createCenterPanel() {
            return myWholePanel;
          }

          public JComponent getPreferredFocusedComponent() {
            return myNameTf;
          }

          protected void doOKAction() {
            if (myNameTf.getText().length() == 0) {
              Messages.showErrorDialog("Enter library copy name", CommonBundle.message("title.error"));
              return;
            }
            super.doOKAction();
          }
        };
        dlg.show();
        if (!dlg.isOK()) return;

        BaseLibrariesConfigurable configurable = mySaveAsCb.isSelected() ? getOppositeGroup() : BaseLibrariesConfigurable.this;

        final LibraryImpl library = (LibraryImpl)myContext.getLibrary(((LibraryImpl)o).getName(), myLevel);

        LOG.assertTrue(library != null);

        final LibraryTable.ModifiableModel libsModel = configurable.getModelProvider().getModifiableModel();
        final Library lib = libsModel.createLibrary(myNameTf.getText());
        final Library.ModifiableModel model = ((LibrariesModifiableModel)libsModel).getLibraryEditor(lib).getModel();
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          final VirtualFile[] files = library.getFiles(type);
          for (VirtualFile file : files) {
            if (mySaveAsCb.isSelected() && myPathTf.getText().trim().length() > 0) {
              final File copy = new File(new File(myPathTf.getText()), file.getName());
              if (!copy.getParentFile().exists() && !copy.getParentFile().mkdirs()) continue;
              try {
                final File fromFile = VfsUtil.virtualToIoFile(file);
                if (fromFile.isFile()) {
                  FileUtil.copy(fromFile, copy);
                } else {
                  FileUtil.copyDir(fromFile, copy);
                }
                model.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copy), type);
              }
              catch (IOException e1) {
                LOG.error(e1);
              }

              continue;
            }

            model.addRoot(file, type);
          }
        }

      }
    }

    public void update(final AnActionEvent e) {
      if (myTree.getSelectionPaths() == null || myTree.getSelectionPaths().length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        e.getPresentation().setEnabled(getSelectedObject() instanceof LibraryImpl);
      }
    }
  }
}
