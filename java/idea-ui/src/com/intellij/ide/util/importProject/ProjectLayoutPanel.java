/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
abstract class ProjectLayoutPanel<T> extends JPanel {

  private final ElementsChooser<T> myEntriesChooser;
  private final JList myDependenciesList;
  private final ModuleInsight myInsight;
  
  private final Comparator<T> COMPARATOR = (o1, o2) -> {
    final int w1 = getWeight(o1);
    final int w2 = getWeight(o2);
    if (w1 != w2) {
      return w1 - w2;
    }
    return getElementText(o1).compareToIgnoreCase(getElementText(o2));
  };

  public ProjectLayoutPanel(final ModuleInsight insight) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
    myInsight = insight;

    myEntriesChooser = new ElementsChooser<T>(true) {
      public String getItemText(@NotNull T element) {
        return getElementText(element);
      }
    };
    myDependenciesList = createList();

    final Splitter splitter = new Splitter(false);

    final JPanel entriesPanel = new JPanel(new BorderLayout());
    entriesPanel.add(myEntriesChooser, BorderLayout.CENTER);
    entriesPanel.setBorder(IdeBorderFactory.createTitledBorder(StringUtil.capitalize(StringUtil.pluralize(getElementTypeName())), false));
    splitter.setFirstComponent(entriesPanel);

    final JScrollPane depsPane = ScrollPaneFactory.createScrollPane(myDependenciesList);
    final JPanel depsPanel = new JPanel(new BorderLayout());
    depsPanel.add(depsPane, BorderLayout.CENTER);
    depsPanel.setBorder(IdeBorderFactory.createTitledBorder(getDependenciesTitle(), false));
    splitter.setSecondComponent(depsPanel);
    
    JPanel groupPanel = new JPanel(new BorderLayout());
    groupPanel.add(createEntriesActionToolbar().getComponent(), BorderLayout.NORTH);
    groupPanel.add(splitter, BorderLayout.CENTER);
    groupPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

    final MultiLineLabel description = new MultiLineLabel(getStepDescriptionText());
    add(description, BorderLayout.NORTH);
    add(groupPanel, BorderLayout.CENTER);
    
    myEntriesChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final List<T> entries = getSelectedEntries();
        final Collection deps = getDependencies(entries);

        final DefaultListModel depsModel = (DefaultListModel)myDependenciesList.getModel();
        depsModel.clear();
        for (Object dep : alphaSortList(new ArrayList(deps))) {
          depsModel.addElement(dep);
        }
      }
    });
  }

  private ActionToolbar createEntriesActionToolbar() {
    final DefaultActionGroup entriesActions = new DefaultActionGroup();

    final RenameAction rename = new RenameAction();
    rename.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F6, KeyEvent.SHIFT_DOWN_MASK)), this);
    entriesActions.add(rename);

    final MergeAction merge = new MergeAction();
    merge.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0)), this);
    entriesActions.add(merge);

    final SplitAction split = new SplitAction();
    split.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0)), this);
    entriesActions.add(split);
    
    return ActionManager.getInstance().createActionToolbar("ProjectLayoutPanel.Entries", entriesActions, true);
  }

  public final ModuleInsight getInsight() {
    return myInsight;
  }

  private JList createList() {
    final JList list = new JBList(new DefaultListModel());
    list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setCellRenderer(new MyListCellRenderer());
    return list;
  }

  public final Collection getDependencies(final List<T> entries) {
    final Set deps = new HashSet();
    for (T et : entries) {
      deps.addAll(getDependencies(et));
    }
    return deps;
  }

  @NotNull
  public List<T> getSelectedEntries() {
    return myEntriesChooser.getSelectedElements();
  }

  @NotNull
  public List<T> getChosenEntries() {
    return myEntriesChooser.getMarkedElements();
  }

  public void rebuild() {
    myEntriesChooser.clear();
    for (final T entry : alphaSortList(getEntries())) {
      myEntriesChooser.addElement(entry, true, new EntryProperties(entry));
    }
    if (myEntriesChooser.getElementCount() > 0) {
      myEntriesChooser.selectElements(Collections.singleton(myEntriesChooser.getElementAt(0)));
    }
  }

  private List<T> alphaSortList(final List<T> entries) {
    Collections.sort(entries, COMPARATOR);
    return entries;
  }

  @Nullable
  protected Icon getElementIcon(Object element) {
    if (element instanceof ModuleDescriptor) {
      return ((ModuleDescriptor)element).getModuleType().getIcon();
    }
    if (element instanceof LibraryDescriptor) {
      return PlatformIcons.LIBRARY_ICON;
    }
    if (element instanceof File) {
      final File file = (File)element;
      return file.isDirectory()? PlatformIcons.DIRECTORY_CLOSED_ICON : PlatformIcons.JAR_ICON;
    }
    return null;
  }

  protected int getWeight(Object element) {
    if (element instanceof File) {
      return 10;
    }
    if (element instanceof ModuleDescriptor) {
      return 20;
    }
    if (element instanceof LibraryDescriptor) {
      return ((LibraryDescriptor)element).getJars().size() > 1? 30 : 40;
    }
    return Integer.MAX_VALUE;
  }

  protected static String getElementText(Object element) {
    if (element instanceof LibraryDescriptor) {
      final StringBuilder builder = new StringBuilder();
      builder.append(((LibraryDescriptor)element).getName());
      final Collection<File> jars = ((LibraryDescriptor)element).getJars();
      if (jars.size() == 1) {
        final File parentFile = jars.iterator().next().getParentFile();
        if (parentFile != null) {
          builder.append(" (");
          builder.append(parentFile.getPath());
          builder.append(")");
        }
      }
      return builder.toString();
    }
    
    if (element instanceof File) {
      final StringBuilder builder = new StringBuilder();
      builder.append(((File)element).getName());
      final File parentFile = ((File)element).getParentFile();
      if (parentFile != null) {
        builder.append(" (");
        builder.append(parentFile.getPath());
        builder.append(")");
      }
      return builder.toString();
    }
    
    if (element instanceof ModuleDescriptor) {
      final ModuleDescriptor moduleDescriptor = (ModuleDescriptor)element;
      final StringBuilder builder = new StringBuilder();
      builder.append(moduleDescriptor.getName());

      final Set<File> contents = moduleDescriptor.getContentRoots();
      final int rootCount = contents.size();
      if (rootCount > 0) {
        builder.append(" (");
        builder.append(contents.iterator().next().getPath());
        if (rootCount > 1) {
          builder.append("...");
        }
        builder.append(")");
      }

      final Collection<? extends DetectedProjectRoot> sourceRoots = moduleDescriptor.getSourceRoots();
      if (sourceRoots.size() > 0) {
        builder.append(" [");
        for (Iterator<? extends DetectedProjectRoot> it = sourceRoots.iterator(); it.hasNext();) {
          DetectedProjectRoot root = it.next();
          builder.append(root.getDirectory().getName());
          if (it.hasNext()) {
            builder.append(",");
          }
        }
        builder.append("]");
      }
      return builder.toString();
    }
    
    return "";
  }

  protected abstract List<T> getEntries();

  protected abstract Collection getDependencies(T entry);

  @Nullable
  protected abstract T merge(List<T> entries);

  @Nullable
  protected abstract T split(T entry, String newEntryName, Collection<File> extractedData);

  protected abstract Collection<File> getContent(T entry);

  protected abstract String getElementName(T entry);

  protected abstract void setElementName(T entry, String name);

  protected abstract String getSplitDialogChooseFilesPrompt();

  protected abstract String getNameAlreadyUsedMessage(final String name);

  protected abstract String getStepDescriptionText();
  
  protected abstract String getEntriesChooserTitle();
  
  protected abstract String getDependenciesTitle();
  
  protected abstract String getElementTypeName();

  private boolean isNameAlreadyUsed(String entryName) {
    final Set<T> itemsToIgnore = new HashSet<>(myEntriesChooser.getSelectedElements());
    for (T entry : getEntries()) {
      if (itemsToIgnore.contains(entry)) {
        continue;
      }
      if (entryName.equals(getElementName(entry))) {
        return true;
      }
    }
    return false;
  }

  private class MergeAction extends AnAction {
    private MergeAction() {
      super("Merge", "", AllIcons.Modules.Merge); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      if (elements.size() > 1) {
        final String newName = Messages.showInputDialog(
          ProjectLayoutPanel.this,
          "Enter new name for merge result:",
          "Merge",
          Messages.getQuestionIcon(), getElementName(elements.get(0)), new InputValidator() {
          public boolean checkInput(final String inputString) {
            return true;
          }

          public boolean canClose(final String inputString) {
            if (isNameAlreadyUsed(inputString.trim())) {
              Messages.showErrorDialog(getNameAlreadyUsedMessage(inputString), "");
              return false;
            }
            return true;
          }
        });
        if (newName != null) {
          final T merged = merge(elements);
          setElementName(merged, newName);
          for (T element : elements) {
            myEntriesChooser.removeElement(element);
          }
          myEntriesChooser.addElement(merged, true, new EntryProperties(merged));
          myEntriesChooser.sort(COMPARATOR);
          myEntriesChooser.selectElements(Collections.singleton(merged));
        }
      }
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEntriesChooser.getSelectedElements().size() > 1);
    }

  }

  private class SplitAction extends AnAction {
    private SplitAction() {
      super("Split", "", AllIcons.Modules.Split); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();

      if (elements.size() == 1) {
        final T entry = elements.get(0);
        final Collection<File> files = getContent(entry);

        final SplitDialog dialog = new SplitDialog(files);
        if (dialog.showAndGet()) {
          final String newName = dialog.getName();
          final Collection<File> chosenFiles = dialog.getChosenFiles();

          final T extracted = split(entry, newName, chosenFiles);
          if (extracted != null) {
            if (!getEntries().contains(entry)) {
              myEntriesChooser.removeElement(entry);
            }
            myEntriesChooser.addElement(extracted, true, new EntryProperties(extracted));
            myEntriesChooser.sort(COMPARATOR);
            myEntriesChooser.selectElements(Collections.singleton(extracted));
          }
        }
      }
    }
    public void update(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      e.getPresentation().setEnabled(elements.size() == 1 && getContent(elements.get(0)).size() > 1);
    }
  }

  private class RenameAction extends AnAction {
    private RenameAction() {
      super("Rename", "", IconUtil.getEditIcon()); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      if (elements.size() == 1) {
        final T element = elements.get(0);
        final String newName = Messages.showInputDialog(ProjectLayoutPanel.this, "New name for " + getElementTypeName() + " '" + getElementName(element) + "':",
          "Rename " + StringUtil.capitalize(getElementTypeName()),
          Messages.getQuestionIcon(),
          getElementName(element),
          new InputValidator() {
            public boolean checkInput(final String inputString) {
              return true;
            }

            public boolean canClose(final String inputString) {
              if (isNameAlreadyUsed(inputString.trim())) {
                Messages.showErrorDialog(getNameAlreadyUsedMessage(inputString), "");
                return false;
              }
              return true;
            }
          }
        );
        if (newName != null) {
          setElementName(element, newName);
          myEntriesChooser.sort(COMPARATOR);
          myEntriesChooser.selectElements(Collections.singleton(element));
        }
      }
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEntriesChooser.getSelectedElements().size() == 1);
    }
  }

  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(getElementText(value));
      setIcon(getElementIcon(value));
      return comp;
    }
  }

  private class SplitDialog extends DialogWrapper {
    final JTextField myNameField;
    final ElementsChooser<File> myChooser;

    private SplitDialog(final Collection<File> files) {
      super(myEntriesChooser, true);
      setTitle("Split " + StringUtil.capitalize(getElementTypeName()));

      myNameField = new JTextField();
      myChooser = new ElementsChooser<File>(true) {
        protected String getItemText(@NotNull final File value) {
          return getElementText(value);
        }
      };
      for (final File file : files) {
        myChooser.addElement(file, false, new ElementsChooser.ElementProperties() {
          public Icon getIcon() {
            return getElementIcon(file);
          }
        });
      }
      myChooser.selectElements(ContainerUtil.createMaybeSingletonList(ContainerUtil.getFirstItem(files)));
      myChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<File>() {
        @Override
        public void elementMarkChanged(File element, boolean isMarked) {
          updateOkButton();
        }
      });
      myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          updateOkButton();
        }
      });

      init();
      updateOkButton();
    }

    private void updateOkButton() {
      setOKActionEnabled(!getName().isEmpty() && !getChosenFiles().isEmpty());
    }

    protected void doOKAction() {
      final String name = getName();
      if (isNameAlreadyUsed(name)) {
        Messages.showErrorDialog(getNameAlreadyUsedMessage(name), "");
        return;
      }
      super.doOKAction();
    }

    @Nullable
    protected JComponent createCenterPanel() {
      FormBuilder builder = FormBuilder.createFormBuilder().setVertical(true);
      builder.addLabeledComponent("&Name:", myNameField);
      builder.addLabeledComponent(getSplitDialogChooseFilesPrompt(), myChooser);
      myChooser.setPreferredSize(JBUI.size(450, 300));
      return builder.getPanel();
    }

    public JComponent getPreferredFocusedComponent() {
      return myNameField;
    }

    public String getName() {
      return myNameField.getText().trim();
    }

    public Collection<File> getChosenFiles() {
      return myChooser.getMarkedElements();
    }
  }

  private class EntryProperties implements ElementsChooser.ElementProperties {
    private final T myEntry;

    public EntryProperties(final T entry) {
      myEntry = entry;
    }

    public Icon getIcon() {
      return getElementIcon(myEntry);
    }
  }
}
