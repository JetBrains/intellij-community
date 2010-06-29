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
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBList;
import com.intellij.util.Icons;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
abstract class ProjectLayoutPanel<T> extends JPanel {
  private static final Icon ICON_MODULE = IconLoader.getIcon("/nodes/ModuleClosed.png");
  private static final Icon RENAME_ICON = IconLoader.getIcon("/modules/edit.png");
  private static final Icon MERGE_ICON = IconLoader.getIcon("/modules/merge.png");
  private static final Icon SPLIT_ICON = IconLoader.getIcon("/modules/split.png");
  
  private final ElementsChooser<T> myEntriesChooser;
  private final JList myDependenciesList;
  private final ModuleInsight myInsight;
  
  private final Comparator<T> COMPARATOR = new Comparator<T>() {
    public int compare(final T o1, final T o2) {
      final int w1 = getWeight(o1);
      final int w2 = getWeight(o2);
      if (w1 != w2) {
        return w1 - w2;
      }
      return getElementText(o1).compareToIgnoreCase(getElementText(o2));
    }
  };

  public ProjectLayoutPanel(final ModuleInsight insight) {
    super(new BorderLayout());
    myInsight = insight;

    myEntriesChooser = new ElementsChooser<T>(true) {
      public String getItemText(@NotNull T element) {
        return getElementText(element);
      }
    };
    myDependenciesList = createList();

    final Splitter splitter = new Splitter(false);
    
    final JScrollPane entriesPane = new JScrollPane(myEntriesChooser);
    entriesPane.setBorder(BorderFactory.createTitledBorder(getEntriesChooserTitle()));
    splitter.setFirstComponent(entriesPane);
    
    final JScrollPane depsPane = new JScrollPane(myDependenciesList);
    depsPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(getDependenciesTitle()), depsPane.getBorder()));
    splitter.setSecondComponent(depsPane);
    
    JPanel groupPanel = new JPanel(new BorderLayout());
    groupPanel.add(createEntriesActionToolbar().getComponent(), BorderLayout.NORTH);
    groupPanel.add(splitter, BorderLayout.CENTER);
    groupPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

    final MultiLineLabel description = new MultiLineLabel(getStepDescriptionText());
    description.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
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

  public List<T> getSelectedEntries() {
    return myEntriesChooser.getSelectedElements();
  }

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
      return ICON_MODULE;
    }
    if (element instanceof LibraryDescriptor) {
      return Icons.LIBRARY_ICON;
    }
    if (element instanceof File) {
      final File file = (File)element;
      return file.isDirectory()? Icons.DIRECTORY_CLOSED_ICON : Icons.JAR_ICON;
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
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
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
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    
    if (element instanceof File) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(((File)element).getName());
        final File parentFile = ((File)element).getParentFile();
        if (parentFile != null) {
          builder.append(" (");
          builder.append(parentFile.getPath());
          builder.append(")");
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    
    if (element instanceof ModuleDescriptor) {
      final ModuleDescriptor moduleDescriptor = (ModuleDescriptor)element;
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
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

        final Set<File> sourceRoots = moduleDescriptor.getSourceRoots();
        if (sourceRoots.size() > 0) {
          builder.append(" [");
          for (Iterator<File> it = sourceRoots.iterator(); it.hasNext();) {
            File root = it.next();
            builder.append(root.getName());
            if (it.hasNext()) {
              builder.append(",");
            }
          }
          builder.append("]");
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
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

  protected abstract String getSplitDialogTitle();

  protected abstract String getSplitDialogChooseFilesPrompt();

  protected abstract String getNameAlreadyUsedMessage(final String name);

  protected abstract String getStepDescriptionText();
  
  protected abstract String getEntriesChooserTitle();
  
  protected abstract String getDependenciesTitle();
  
  private boolean isNameAlreadyUsed(String entryName) {
    final Set<T> itemsToIgnore = new HashSet<T>(myEntriesChooser.getSelectedElements());
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
      super("Merge", "", MERGE_ICON); // todo
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
      super("Split", "", SPLIT_ICON); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();

      if (elements.size() == 1) {
        final T entry = elements.get(0);
        final Collection<File> files = getContent(entry);

        final SplitDialog dialog = new SplitDialog(files);
        dialog.show();

        if (dialog.isOK()) {
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
      super.update(e);
      e.getPresentation().setEnabled(myEntriesChooser.getSelectedElements().size() == 1);
    }
  }

  private class RenameAction extends AnAction {
    private RenameAction() {
      super("Rename", "", RENAME_ICON); // todo
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<T> elements = myEntriesChooser.getSelectedElements();
      if (elements.size() == 1) {
        final T element = elements.get(0);
        final String newName = Messages.showInputDialog(
          ProjectLayoutPanel.this,
          "Enter new name for " + getElementText(element),
          "Rename",
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
      setTitle(getSplitDialogTitle());

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
          public Color getColor() {
            return null;
          }
        });
      }
      init();
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
      final JPanel panel = new JPanel(new BorderLayout());

      final JPanel labelNameField = new JPanel(new BorderLayout());
      labelNameField.add(new JLabel("Name:"), BorderLayout.NORTH);
      labelNameField.add(myNameField, BorderLayout.CENTER);
      labelNameField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      final JPanel labelChooser = new JPanel(new BorderLayout());
      labelChooser.add(new JLabel(getSplitDialogChooseFilesPrompt()), BorderLayout.NORTH);
      labelChooser.add(new JScrollPane(myChooser), BorderLayout.CENTER);
      labelChooser.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      panel.add(labelNameField, BorderLayout.NORTH);
      panel.add(labelChooser, BorderLayout.CENTER);
      panel.setPreferredSize(new Dimension(450, 300));
      return panel;
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

    public Color getColor() {
      return null;
    }
  }
}
