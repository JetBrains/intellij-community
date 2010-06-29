/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.ui.classpath;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Icons;
import com.intellij.util.PathUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class SimpleClasspathPanel extends JPanel {

  private final JList myList = new JBList();
  private final DefaultListModel myListModel = new DefaultListModel();
  private URLClassLoader myClassLoader;
  private final Disposable myDisposable;


  public SimpleClasspathPanel(final List<SimpleClasspathElement> classpathElements, final Disposable parentDisposable) {
    myDisposable = parentDisposable;
    for (Library library : getLibrariesList(classpathElements, parentDisposable)) {
      myListModel.addElement(library);
    }
    init();
  }

  private void init() {
    setLayout(new BorderLayout());
    myList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    myList.setModel(myListModel);
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final ReorderableListController<Object> controller = ReorderableListController.create(myList, actionGroup);
    controller.addAction(new AddAction());
    controller.addRemoveAction(ProjectBundle.message("module.remove.action"));
    controller.addMoveUpAction();
    controller.addMoveDownAction();
    customizeToolbarActions(actionGroup);
    myList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Library) {
          final Library library = (Library)value;
          if (library.getName() != null && library.getUrls(OrderRootType.CLASSES).length == 0) {
            SimpleTextCellAppearance.invalid(library.getName(), Icons.LIBRARY_ICON).customize(this);
          }
          else {
            OrderEntryCellAppearanceUtils.forLibrary(library).customize(this);
          }
        }
      }
    });
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, actionGroup, true).getComponent(), BorderLayout.NORTH);
    final JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    add(scrollPane, BorderLayout.CENTER);
    final FontMetrics fontMetrics = myList.getFontMetrics(myList.getFont());
    scrollPane.setPreferredSize(new Dimension(0, fontMetrics.getHeight() * 12));
    myList.getModel().addListDataListener(new ListDataListener() {
      public void intervalAdded(ListDataEvent e) {
      }

      public void intervalRemoved(ListDataEvent e) {
        listChanged(e);
      }

      public void contentsChanged(ListDataEvent e) {
      }
    });
  }

  protected void customizeToolbarActions(DefaultActionGroup actionGroup) {
  }

  private void listChanged(final ListDataEvent e) {
    myClassLoader = null;
    processClasspathChanged();
  }

  public void processClasspathChanged() {
  }

  public List<Library> getOrderedLibraries() {
    final ArrayList<Library> result = new ArrayList<Library>();
    for (final Enumeration<?> enumeration = myListModel.elements(); enumeration.hasMoreElements(); ) {
      result.add((Library)enumeration.nextElement());
    }
    return result;
  }

  public Set<VirtualFile> getVirtualFiles() {
    final THashSet<VirtualFile> result = new THashSet<VirtualFile>();
    for (final Enumeration<?> enumeration = myListModel.elements(); enumeration.hasMoreElements(); ) {
      final Library library = (Library)enumeration.nextElement();
      result.addAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)));
    }
    return result;
  }

  public URLClassLoader getClasspathLoader(ClassLoader parent) {
    if (myClassLoader == null || myClassLoader.getParent() != parent) {
      ArrayList<URL> urlList = new ArrayList<URL>();
      for (Library library : getOrderedLibraries()) {
        for (VirtualFile virtualFile : library.getFiles(OrderRootType.CLASSES)) {
          final File file = new File(PathUtil.toPresentableUrl(virtualFile.getUrl()));
          try {
            urlList.add(file.toURL());
          }
          catch (MalformedURLException e) {
          }
        }
      }
      URL[] urls = urlList.toArray(new URL[urlList.size()]);
      myClassLoader = new URLClassLoader(urls, parent);
    }
    return myClassLoader;
  }

  public static void scrollSelectionToVisible(JList list){
    ListSelectionModel selectionModel = list.getSelectionModel();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    if(maxSelectionIndex == -1){
      return;
    }
    Rectangle cellRect = list.getCellBounds(minSelectionIndex, maxSelectionIndex);
    list.scrollRectToVisible(cellRect);
  }

  private static Collection<Library> ensureApplicationLevel(final Library library, final Set<VirtualFile> existingFiles,
                                                            final Disposable parentDisposable) {
    if (library.getTable() == null || !LibraryTablesRegistrar.APPLICATION_LEVEL.equals(library.getTable().getTableLevel())) {
      final ArrayList<Library> result = new ArrayList<Library>();
      for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
        if (!existingFiles.add(file)) continue;
        final Library newLibrary = LibraryTableImplUtil.createModuleLevelLibrary(null, null);
        Disposer.register(parentDisposable, newLibrary);
        final Library.ModifiableModel libModel = newLibrary.getModifiableModel();
        libModel.addRoot(file, OrderRootType.CLASSES);
        libModel.commit();
        result.add(newLibrary);
      }
      return result;
    }
    return Collections.singletonList(library);
  }

  public abstract static class PopupAction implements ActionListener {
    private final String myTitle;
    private final Icon myIcon;
    private final int myIndex;

    protected PopupAction(String title, Icon icon, final int index) {
      myTitle = title;
      myIcon = icon;
      myIndex = index;
    }

    public String getTitle() {
      return myTitle;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public int getIndex() {
      return myIndex;
    }

    protected abstract void executeImpl();

    public void execute() {
      executeImpl();
    }

    public void actionPerformed(ActionEvent e) {
      executeImpl();
    }
  }

  public static abstract class PopupActionGroupAction extends DumbAwareAction {
    private PopupAction[] myPopupActions;
    private Icon[] myIcons;

    protected PopupActionGroupAction(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    private void initPopupActions() {
      if (myPopupActions == null) {
        myPopupActions = createPopupActions();

        myIcons = new Icon[myPopupActions.length];
        for (int idx = 0; idx < myPopupActions.length; idx++) {
          myIcons[idx] = myPopupActions[idx].getIcon();
        }
      }
    }

    protected abstract PopupAction[] createPopupActions();

    public void actionPerformed(final AnActionEvent e) {
      initPopupActions();
      final JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PopupAction>(null, myPopupActions, myIcons) {
        public boolean isMnemonicsNavigationEnabled() {
          return true;
        }

        public boolean isSelectable(PopupAction value) {
          return true;
        }

        public PopupStep onChosen(final PopupAction selectedValue, final boolean finalChoice) {
          return doFinalStep(new Runnable() {
            public void run() {
              selectedValue.execute();
            }
          });
        }

        @NotNull
        public String getTextFor(PopupAction value) {
          return "&" + value.getIndex() + "  " + value.getTitle();
        }
      });
      popup.showUnderneathOf(e.getInputEvent().getComponent());
    }
  }

  private class AddAction extends PopupActionGroupAction {

    public AddAction() {
      super(ProjectBundle.message("module.add.action"), null, IconLoader.getIcon("/general/add.png"));
    }

    protected PopupAction[] createPopupActions() {
      int index = 1;
      final List<PopupAction> actions = new ArrayList<PopupAction>();
      actions.add(new ChooseAndAddAction(index++, "Jar...", Icons.JAR_ICON) {
        @NotNull
        protected List<Library> doChoose() {
          final ChooseJarDialog dialog = new ChooseJarDialog(SimpleClasspathPanel.this, getVirtualFiles(), myDisposable);
          dialog.doChoose();
          return dialog.getChosenElements();
        }
      });
      actions.add(new ChooseAndAddAction(index++, "Library...", Icons.LIBRARY_ICON) {

        @NotNull
        protected List<Library> doChoose() {
          final Set<VirtualFile> existingFiles = getVirtualFiles();
          final ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(ProjectManager.getInstance().getDefaultProject(), "Choose Existing Libraries") {
            @Override
            protected boolean acceptsElement(final Object element) {
              if (!(element instanceof Library)) return true;
              final Library library = (Library)element;
              return !existingFiles.containsAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)));
            }

            @Override
            protected JComponent createCenterPanel() {
              final JPanel panel = new JPanel(new BorderLayout());
              panel.add(super.createCenterPanel(), BorderLayout.CENTER);
              final MultiLineLabel label = new MultiLineLabel("Please note that project-level and module-level libraries will not be\n\n" +
                                                              "  added as a whole but will be converted to jars and folders instead.");
              label.setIcon(Messages.getWarningIcon());
              label.setIcon(Messages.getWarningIcon());
              panel.add(label, BorderLayout.SOUTH);
              return panel;
            }

          };
          dialog.show();
          final List<Library> libraries = dialog.getSelectedLibraries();
          final ArrayList<Library> result = new ArrayList<Library>();
          for (Library o : libraries) {
            result.addAll(ensureApplicationLevel(o, existingFiles, myDisposable));
          }
          return result;
        }
      });
      return actions.toArray(new PopupAction[actions.size()]);
    }
  }


  private abstract class ChooseAndAddAction extends PopupAction {
    public ChooseAndAddAction(int index, String title, Icon icon) {
      super(title, icon, index);
    }

    protected final void executeImpl() {
      final List<Library> libraries = doChoose();
      if (libraries.isEmpty()) return;
      final int index0 = myListModel.size();
      for (Library library : libraries) {
        myListModel.addElement(library);
      }
      final int index1 = myListModel.size()-1;
      final ListSelectionModel selectionModel = myList.getSelectionModel();
      final int rowCount = myList.getModel().getSize();
      selectionModel.setSelectionInterval(rowCount - libraries.size(), rowCount - 1);
      scrollSelectionToVisible(myList);
      listChanged(new ListDataEvent(myListModel, ListDataEvent.INTERVAL_ADDED, index0, index1));
    }

    @NotNull
    protected abstract List<Library> doChoose();
  }

  private static class ChooseJarDialog extends FileChooserDialogImpl {
    private VirtualFile[] myLastChosen;
    private final Disposable myParentDisposable;

    public ChooseJarDialog(Component parent, final Set<VirtualFile> existingFiles, final Disposable disposable) {
      super(new FileChooserDescriptor(false, true, true, false, false, true) {
        @Override
        public boolean isFileVisible(final VirtualFile file, final boolean showHiddenFiles) {
          if (!super.isFileVisible(file, showHiddenFiles)) return false;
          return file.isDirectory() || !existingFiles.contains(file);
        }
      }, parent);
      myParentDisposable = disposable;
    }

    public List<Library> getChosenElements() {
      if (myLastChosen == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] files = myLastChosen;
      if (files.length == 0) {
        return Collections.emptyList();
      }
      final List<Library> addedLibraries = new ArrayList<Library>(files.length);
      for (VirtualFile file : files) {
        final Library library = LibraryTableImplUtil.createModuleLevelLibrary(null, null);
        Disposer.register(myParentDisposable, library);
        final Library.ModifiableModel libModel = library.getModifiableModel();
        libModel.addRoot(file, OrderRootType.CLASSES);
        libModel.commit();
        addedLibraries.add(library);
      }
      return addedLibraries;
    }

    public void doChoose() {
      myLastChosen = choose(null, null);
    }
  }

  private static List<Library> getLibrariesList(final List<SimpleClasspathElement> classpathElements, final Disposable disposable) {
    ArrayList<Library> result = new ArrayList<Library>();
    for (SimpleClasspathElement classpathElement : classpathElements) {
      final Library library = classpathElement.getLibrary();
      if (library != null && library.getTable() != null) {
        result.add(library);
      }
      else {
        final Library newLibrary = LibraryTableImplUtil.createModuleLevelLibrary(null, null);
        Disposer.register(disposable, newLibrary);
        final Library.ModifiableModel libModel = newLibrary.getModifiableModel();
        final String libName = classpathElement.getLibraryName();
        if (libName != null) libModel.setName(libName);
        List<String> fileUrls = classpathElement.getClassesRootUrls();
        for (final String fileUrl : fileUrls) {
          libModel.addRoot(fileUrl, OrderRootType.CLASSES);
        }
        libModel.commit();
        result.add(newLibrary);
      }
    }
    return result;
  }

}
