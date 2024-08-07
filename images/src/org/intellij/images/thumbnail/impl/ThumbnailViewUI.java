// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.impl;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.CopyPasteSupport;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.*;
import org.intellij.images.search.ImageTagManager;
import org.intellij.images.search.TagFilter;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions;
import org.intellij.images.thumbnail.actions.ThemeFilter;
import org.intellij.images.thumbnail.actions.ToggleTagsPanelAction;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.ui.ImageComponentDecorator;
import org.intellij.images.ui.ThumbnailComponent;
import org.intellij.images.ui.ThumbnailComponentUI;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

import static com.intellij.pom.Navigatable.EMPTY_NAVIGATABLE_ARRAY;

final class ThumbnailViewUI extends JPanel implements UiDataProvider, Disposable {

  private final ThumbnailView thumbnailView;
  private final CopyPasteSupport copyPasteSupport;
  private final DeleteProvider deleteProvider;
  private ThumbnailListCellRenderer cellRenderer;
  private JBList<VirtualFile> list;
  private JPanel tagsPanel;
  private static final Comparator<VirtualFile> VIRTUAL_FILE_COMPARATOR = (o1, o2) -> {
    if (o1.isDirectory() && !o2.isDirectory()) {
      return -1;
    }
    if (o2.isDirectory() && !o1.isDirectory()) {
      return 1;
    }

    return o1.getPath().compareToIgnoreCase(o2.getPath());
  };
  private DefaultListModel<String> listModel;
  private Splitter previewSplitter;

  ThumbnailViewUI(ThumbnailViewImpl thumbnailView) {
    super(new BorderLayout());

    this.thumbnailView = thumbnailView;

    copyPasteSupport = new CopyPasteDelegator(thumbnailView.getProject(), this);
    deleteProvider = new DeleteHandler.DefaultDeleteProvider();
  }

  private void createUI() {
    if (cellRenderer == null || list == null) {
      cellRenderer = new ThumbnailListCellRenderer();
      ImageComponent imageComponent = cellRenderer.getImageComponent();

      VirtualFileManager.getInstance().addVirtualFileListener(new VFSListener(), this);

      Options options = OptionsManager.getInstance().getOptions();
      EditorOptions editorOptions = options.getEditorOptions();
      // Set options
      TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
      imageComponent.setTransparencyChessboardVisible(chessboardOptions.isShowDefault());
      imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
      imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
      imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
      imageComponent.setFileNameVisible(editorOptions.isFileNameVisible());
      imageComponent.setFileSizeVisible(editorOptions.isFileSizeVisible());

      options.addPropertyChangeListener(new OptionsChangeListener(), this);

      list = new JBList<>();
      list.setModel(new DefaultListModel<>());
      list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
      list.setVisibleRowCount(-1);
      list.setCellRenderer(cellRenderer);
      list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      list.addListSelectionListener(e -> updateTagsPreviewModel());

      ThumbnailsMouseAdapter mouseListener = new ThumbnailsMouseAdapter();
      list.addMouseListener(mouseListener);
      list.addMouseMotionListener(mouseListener);

      ThumbnailComponentUI componentUI = (ThumbnailComponentUI)ThumbnailComponentUI.createUI(cellRenderer);
      Dimension preferredSize = componentUI.getPreferredSize(cellRenderer);

      list.setFixedCellWidth(preferredSize.width);
      list.setFixedCellHeight(preferredSize.height);


      JScrollPane scrollPane =
        ScrollPaneFactory.createScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                           ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ThumbnailViewActions.GROUP_TOOLBAR);
      ActionToolbar actionToolbar = actionManager.createActionToolbar(
        ThumbnailViewActions.ACTION_PLACE, actionGroup, true
      );
      actionToolbar.setTargetComponent(this);

      JComponent toolbar = actionToolbar.getComponent();

      FocusRequester focusRequester = new FocusRequester();
      toolbar.addMouseListener(focusRequester);
      scrollPane.addMouseListener(focusRequester);

      add(toolbar, BorderLayout.NORTH);

      previewSplitter = new Splitter();
      previewSplitter.setFirstComponent(scrollPane);
      previewSplitter.setProportion(1);
      previewSplitter.setSecondComponent(null);
      add(previewSplitter, BorderLayout.CENTER);
    }
    updateTagsPreview();
  }

  private JPanel createTagPreviewPanel() {
    listModel = new DefaultListModel<>();
    updateTagsPreviewModel();
    JBList<String> tagsList = new JBList<>(listModel);
    tagsList.setEmptyText(ImagesBundle.message("list.empty.text.no.tags.defined"));
    ImageTagManager imageTagManager = ImageTagManager.getInstance(thumbnailView.getProject());
    return ToolbarDecorator.createDecorator(tagsList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("popup.title.add.tags"),
                                                              new AddTagGroup(),
                                                              button.getDataContext(),
                                                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
            .show(button.getPreferredPopupPoint());
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          String selectedValue = tagsList.getSelectedValue();
          if (selectedValue != null) {
            Arrays.stream(getSelection())
              .forEach(virtualFile -> imageTagManager.removeTag(selectedValue, virtualFile));
          }
          updateTagsPreviewModel();
        }
      })
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.RIGHT)
      .createPanel();
  }

  private void updateTagsPreview() {
    Project project = thumbnailView.getProject();
    boolean enabled = PropertiesComponent.getInstance(project).getBoolean(ToggleTagsPanelAction.TAGS_PANEL_VISIBLE, false);
    float splitterProportion = previewSplitter.getProportion();
    if (enabled) {
      if (splitterProportion == 1) {
        previewSplitter.setProportion(
          Float.parseFloat(PropertiesComponent.getInstance(project).getValue(ToggleTagsPanelAction.TAGS_PANEL_PROPORTION, "0.5f")));
      }
      if (tagsPanel == null) {
        tagsPanel = createTagPreviewPanel();
      }
      previewSplitter.setSecondComponent(tagsPanel);
    }
    else {
      if (splitterProportion != 1) {
        PropertiesComponent.getInstance(thumbnailView.getProject())
          .setValue(ToggleTagsPanelAction.TAGS_PANEL_PROPORTION, String.valueOf(splitterProportion));
      }
      previewSplitter.setProportion(1);
      previewSplitter.setSecondComponent(null);
    }
  }

  private void updateTagsPreviewModel() {
    if (listModel == null) return;
    listModel.clear();

    VirtualFile[] selection = getSelection();
    ImageTagManager tagManager = ImageTagManager.getInstance(thumbnailView.getProject());
    List<String> commonTags = null;
    for (VirtualFile virtualFile : selection) {
      List<String> tags = tagManager.getTags(virtualFile);
      if (commonTags == null) {
        commonTags = new ArrayList<>(tags);
      }
      else {
        commonTags.retainAll(tags);
      }
    }

    if (commonTags != null) {
      commonTags.forEach(listModel::addElement);
    }
  }

  public void refresh() {
    createUI();
    if (list != null) {
      DefaultListModel<VirtualFile> model = (DefaultListModel<VirtualFile>)list.getModel();
      model.clear();
      VirtualFile root = thumbnailView.getRoot();
      if (root != null && root.isValid() && root.isDirectory()) {
        Set<VirtualFile> files = findFiles(root.getChildren());
        VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(files);
        Arrays.sort(virtualFiles, VIRTUAL_FILE_COMPARATOR);

        model.ensureCapacity(model.size() + virtualFiles.length + 1);
        ThemeFilter filter = thumbnailView.getFilter();
        TagFilter[] tagFilters = thumbnailView.getTagFilters();
        for (VirtualFile virtualFile : virtualFiles) {
          if (filter == null || filter.accepts(virtualFile)) {
            if (tagFilters == null || ContainerUtil.exists(tagFilters, tagFilter -> tagFilter.accepts(virtualFile))) {
              model.addElement(virtualFile);
            }
          }
        }
        if (!model.isEmpty()) {
          list.setSelectedIndex(0);
        }
      }
      else {
        thumbnailView.setVisible(false);
      }
    }
  }

  public boolean isTransparencyChessboardVisible() {
    createUI();
    return cellRenderer.getImageComponent().isTransparencyChessboardVisible();
  }

  public void setTransparencyChessboardVisible(boolean visible) {
    createUI();
    cellRenderer.getImageComponent().setTransparencyChessboardVisible(visible);
    list.repaint();
  }

  public void setFileNameVisible(boolean visible) {
    createUI();
    cellRenderer.getImageComponent().setFileNameVisible(visible);
    list.repaint();
  }

  public boolean isFileNameVisible() {
    createUI();
    return cellRenderer.getImageComponent().isFileNameVisible();
  }

  public void setFileSizeVisible(boolean visible) {
    createUI();
    cellRenderer.getImageComponent().setFileSizeVisible(visible);
    list.repaint();
  }

  public boolean isFileSizeVisible() {
    createUI();
    return cellRenderer.getImageComponent().isFileSizeVisible();
  }

  public void setSelected(VirtualFile file) {
    createUI();
    list.setSelectedValue(file, false);
  }

  public void scrollToSelection() {
    int minSelectionIndex = list.getMinSelectionIndex();
    int maxSelectionIndex = list.getMaxSelectionIndex();
    if (minSelectionIndex != -1 && maxSelectionIndex != -1) {
      list.scrollRectToVisible(list.getCellBounds(minSelectionIndex, maxSelectionIndex));
    }
  }

  public boolean isSelected(VirtualFile file) {
    int index = ((DefaultListModel<?>)list.getModel()).indexOf(file);
    return index != -1 && list.isSelectedIndex(index);
  }

  public VirtualFile @NotNull [] getSelection() {
    List<VirtualFile> list = this.list == null ? Collections.emptyList() : this.list.getSelectedValuesList();
    return list.toArray(VirtualFile.EMPTY_ARRAY);
  }

  private final class ThumbnailListCellRenderer extends ThumbnailComponent
    implements ListCellRenderer<VirtualFile> {
    private final ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();

    @Override
    public Component getListCellRendererComponent(
      JList list, VirtualFile file, int index, boolean isSelected, boolean cellHasFocus) {
      if (file != null) {
        setFileName(file.getName());
        String toolTipText = IfsUtil.getReferencePath(thumbnailView.getProject(), file);
        if (!isFileSizeVisible()) {
          String description = getImageComponent().getDescription();
          if (description != null) {
            toolTipText += " [" + description + "]";
          }
        }
        setToolTipText(toolTipText);
        setDirectory(file.isDirectory());
        if (file.isDirectory()) {
          int imagesCount = 0;
          VirtualFile[] children = file.getChildren();
          for (VirtualFile child : children) {
            if (typeManager.isImage(child)) {
              imagesCount++;
              if (imagesCount > 100) {
                break;
              }
            }
          }
          setImagesCount(imagesCount);
        }
        else {
          // File rendering
          setFileSize(file.getLength());
          try {
            ImageComponent imageComponent = getImageComponent();
            BufferedImage image = IfsUtil.getImage(file, imageComponent);
            imageComponent.getDocument().setValue(image);
            setFormat(IfsUtil.getFormat(file));
          }
          catch (Exception e) {
            // Ignore
            ImageComponent imageComponent = getImageComponent();
            imageComponent.getDocument().setValue((BufferedImage)null);
          }
        }
      }
      else {
        ImageComponent imageComponent = getImageComponent();
        imageComponent.getDocument().setValue((BufferedImage)null);
        setFileName(null);
        setFileSize(0);
        setToolTipText(null);
      }

      if (isSelected) {
        setForeground(list.getSelectionForeground());
        setBackground(list.getSelectionBackground());
      }
      else {
        setForeground(list.getForeground());
        setBackground(list.getBackground());
      }

      return this;
    }
  }

  private Set<VirtualFile> findFiles(VirtualFile[] roots) {
    Set<VirtualFile> files = new HashSet<>();
    for (VirtualFile root : roots) {
      files.addAll(findFiles(root));
    }
    return files;
  }

  private Set<VirtualFile> findFiles(VirtualFile file) {
    Set<VirtualFile> files = new HashSet<>(0);
    Project project = thumbnailView.getProject();
    if (!project.isDisposed()) {
      ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
      boolean projectIgnored = rootManager.getFileIndex().isExcluded(file);

      if (!projectIgnored && !FileTypeManager.getInstance().isFileIgnored(file)) {
        ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
        if (file.isDirectory()) {
          if (thumbnailView.isRecursive()) {
            files.addAll(findFiles(file.getChildren()));
          }
          else if (isImagesInDirectory(file)) {
            files.add(file);
          }
        }
        else if (typeManager.isImage(file)) {
          files.add(file);
        }
      }
    }
    return files;
  }

  private static boolean isImagesInDirectory(VirtualFile dir) {
    ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
    VirtualFile[] files = dir.getChildren();
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        // We can be sure for fast searching
        return true;
      }
      if (typeManager.isImage(file)) {
        return true;
      }
    }
    return false;
  }

  private final class ThumbnailsMouseAdapter extends MouseAdapter {
    @Override
    public void mouseDragged(MouseEvent e) {
      clearSelectionOutsideIfNotWithControl(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      clearSelectionOutsideIfNotWithControl(e);
    }

    private void clearSelectionOutsideIfNotWithControl(@NotNull MouseEvent e) {
      Point point = e.getPoint();
      int index = list.locationToIndex(point);
      if (index != -1) {
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (!cellBounds.contains(point) &&
            (InputEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != InputEvent.CTRL_DOWN_MASK) {
          list.clearSelection();
          e.consume();
        }
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      Point point = e.getPoint();
      int index = list.locationToIndex(point);
      if (index != -1) {
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (!cellBounds.contains(point) && (InputEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != InputEvent.CTRL_DOWN_MASK) {
          index = -1;
          list.clearSelection();
        }
      }
      if (index != -1) {
        if (MouseEvent.BUTTON1 == e.getButton() && e.getClickCount() == 2) {
          // Double click
          list.setSelectedIndex(index);
          VirtualFile selected = list.getSelectedValue();
          if (selected != null) {
            if (selected.isDirectory()) {
              thumbnailView.setRoot(selected);
            }
            else {
              FileEditorManager fileEditorManager = FileEditorManager.getInstance(thumbnailView.getProject());
              fileEditorManager.openFile(selected, true);
            }
            e.consume();
          }
        }
        if (MouseEvent.BUTTON3 == e.getButton() && e.getClickCount() == 1) {
          // Ensure that we have selection
          if ((InputEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != InputEvent.CTRL_DOWN_MASK) {
            // Ctrl is not pressed
            list.setSelectedIndex(index);
          }
          else {
            // Ctrl is pressed
            list.getSelectionModel().addSelectionInterval(index, index);
          }
          // Single right click
          ActionManager actionManager = ActionManager.getInstance();
          ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ThumbnailViewActions.GROUP_POPUP);
          ActionPopupMenu menu = actionManager.createActionPopupMenu(ThumbnailViewActions.ACTION_PLACE, actionGroup);
          JPopupMenu popupMenu = menu.getComponent();
          popupMenu.pack();
          JBPopupMenu.showByEvent(e, popupMenu);
          e.consume();
        }
      }
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    Project project = thumbnailView.getProject();
    VirtualFile @NotNull [] selection = getSelection();

    sink.set(CommonDataKeys.PROJECT, project);
    sink.set(CommonDataKeys.VIRTUAL_FILE,
             selection.length > 0 ? selection[0] : null);
    sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, selection);
    sink.set(CommonDataKeys.NAVIGATABLE,
             new ThumbnailNavigatable(selection.length > 0 ? selection[0] : null));
    sink.set(PlatformDataKeys.COPY_PROVIDER, copyPasteSupport.getCopyProvider());
    sink.set(PlatformDataKeys.CUT_PROVIDER, copyPasteSupport.getCutProvider());
    sink.set(PlatformDataKeys.PASTE_PROVIDER, copyPasteSupport.getPasteProvider());
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, deleteProvider);
    sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, JBIterable.of(selection)
      .filter(o -> !o.isDirectory())
      .map(o -> (Navigatable)new ThumbnailNavigatable(o))
      .unique()
      .toArray(EMPTY_NAVIGATABLE_ARRAY));
    sink.set(ThumbnailView.DATA_KEY, thumbnailView);
    sink.set(ImageComponentDecorator.DATA_KEY, thumbnailView);

    sink.lazy(CommonDataKeys.PSI_FILE, () -> {
      return selection.length > 0 ? PsiManager.getInstance(project).findFile(selection[0]) : null;
    });
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      return selection.length > 0 ? PsiManager.getInstance(project).findFile(selection[0]) : null;
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      PsiManager psiManager = PsiManager.getInstance(project);
      return JBIterable.of(selection)
        .filterMap(o -> (PsiElement)(o.isDirectory() ? psiManager.findDirectory(o) : psiManager.findFile(o)))
        .unique()
        .toArray(PsiElement.EMPTY_ARRAY);
    });
  }

  @Override
  public void dispose() {
    removeAll();

    list = null;
    cellRenderer = null;
    tagsPanel = null;
  }

  private final class ThumbnailNavigatable implements Navigatable {
    private final VirtualFile file;

    ThumbnailNavigatable(@Nullable VirtualFile file) {
      this.file = file;
    }

    @Override
    public void navigate(boolean requestFocus) {
      if (file != null) {
        FileEditorManager manager = FileEditorManager.getInstance(thumbnailView.getProject());
        manager.openFile(file, true);
      }
    }

    @Override
    public boolean canNavigate() {
      return file != null;
    }

    @Override
    public boolean canNavigateToSource() {
      return file != null;
    }
  }

  private final class VFSListener implements VirtualFileListener {
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (list != null) {
        int index = ((DefaultListModel<?>)list.getModel()).indexOf(file);
        if (index != -1) {
          Rectangle cellBounds = list.getCellBounds(index, index);
          list.repaint(cellBounds);
        }
      }
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      VirtualFile root = thumbnailView.getRoot();
      if (root != null && VfsUtilCore.isAncestor(file, root, false)) {
        refresh();
      }
      if (list != null) {
        ((DefaultListModel<?>)list.getModel()).removeElement(file);
      }
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      refresh();
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      refresh();
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      refresh();
    }
  }

  private final class OptionsChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      Options options = (Options)evt.getSource();
      EditorOptions editorOptions = options.getEditorOptions();
      TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
      GridOptions gridOptions = editorOptions.getGridOptions();

      ImageComponent imageComponent = cellRenderer.getImageComponent();
      imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
      imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
      imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
      imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
      imageComponent.setGridLineSpan(gridOptions.getLineSpan());
      imageComponent.setGridLineColor(gridOptions.getLineColor());
    }
  }

  private class FocusRequester extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      IdeFocusManager.getGlobalInstance()
        .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(ThumbnailViewUI.this, true));
    }
  }

  public class AddTagGroup extends ActionGroup {
    public AddTagGroup() {
      setPopup(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return EMPTY_ARRAY;
      Project project = e.getProject();
      if (project == null) return EMPTY_ARRAY;
      ImageTagManager tagManager = ImageTagManager.getInstance(project);
      List<String> tags = tagManager.getAllTags();
      int tagsNumber = tags.size();
      AnAction[] actions = new AnAction[tagsNumber + 1];
      for (int i = 0; i < tagsNumber; i++) {
        String tag = tags.get(i);
        actions[i] = new AnAction(tag) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            for (VirtualFile file : thumbnailView.getSelection()) {
              tagManager.addTag(tag, file);
            }

            updateTagsPreviewModel();
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation()
              .setEnabledAndVisible(!ContainerUtil.exists(thumbnailView.getSelection(), file -> tagManager.hasTag(tag, file)));
          }

          @Override
          public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
          }
        };
      }
      actions[tagsNumber] = new AnAction(IdeBundle.messagePointer("action.Anonymous.text.new.tag")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
          if (view != null) {
            VirtualFile[] selection = view.getSelection();
            if (selection.length > 0) {
              String tag = Messages.showInputDialog("", IdeBundle.message("dialog.title.new.tag.name"), null);
              if (tag != null) {
                for (VirtualFile file : selection) {
                  tagManager.addTag(tag, file);
                }
              }
            }
          }
        }
      };

      return actions;
    }
  }
}
