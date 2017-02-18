/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/** $Id$ */

package org.intellij.images.thumbnail.impl;

import com.intellij.ide.CopyPasteSupport;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PsiActionSupportFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBList;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.*;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.ui.ImageComponentDecorator;
import org.intellij.images.ui.ThumbnailComponent;
import org.intellij.images.ui.ThumbnailComponentUI;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

final class ThumbnailViewUI extends JPanel implements DataProvider, Disposable {
    private final VirtualFileListener vfsListener = new VFSListener();
    private final OptionsChangeListener optionsListener = new OptionsChangeListener();

    private static final Navigatable[] EMPTY_NAVIGATABLE_ARRAY = new Navigatable[]{};

    private final ThumbnailView thumbnailView;
    private final CopyPasteSupport copyPasteSupport;
    private final DeleteProvider deleteProvider;
    private ThumbnailListCellRenderer cellRenderer;
    private JList list;
    private static final Comparator<VirtualFile> VIRTUAL_FILE_COMPARATOR = (o1, o2) -> {
        if (o1.isDirectory() && !o2.isDirectory()) {
            return -1;
        }
        if (o2.isDirectory() && !o1.isDirectory()) {
            return 1;
        }

        return o1.getPath().toLowerCase().compareTo(o2.getPath().toLowerCase());
    };

    public ThumbnailViewUI(ThumbnailViewImpl thumbnailView) {
        super(new BorderLayout());

        this.thumbnailView = thumbnailView;

        final PsiActionSupportFactory factory = PsiActionSupportFactory.getInstance();
        copyPasteSupport = factory.createPsiBasedCopyPasteSupport(thumbnailView.getProject(), this, new PsiActionSupportFactory.PsiElementSelector() {
            public PsiElement[] getSelectedElements() {
                return (PsiElement[]) getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
            }
        });

        deleteProvider = factory.createPsiBasedDeleteProvider();

    }

    private void createUI() {
        if (cellRenderer == null || list == null) {
            cellRenderer = new ThumbnailListCellRenderer();
            ImageComponent imageComponent = cellRenderer.getImageComponent();

            VirtualFileManager.getInstance().addVirtualFileListener(vfsListener);

            Options options = OptionsManager.getInstance().getOptions();
            EditorOptions editorOptions = options.getEditorOptions();
            // Set options
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
            imageComponent.setTransparencyChessboardVisible(chessboardOptions.isShowDefault());
            imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
            imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
            imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());

            options.addPropertyChangeListener(optionsListener);

            list = new JBList();
            list.setModel(new DefaultListModel());
            list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            list.setVisibleRowCount(-1);
            list.setCellRenderer(cellRenderer);
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

            ThumbnailsMouseAdapter mouseListener = new ThumbnailsMouseAdapter();
            list.addMouseListener(mouseListener);
            list.addMouseMotionListener(mouseListener);

            ThumbnailComponentUI componentUI = (ThumbnailComponentUI) UIManager.getUI(cellRenderer);
            Dimension preferredSize = componentUI.getPreferredSize(cellRenderer);

            list.setFixedCellWidth(preferredSize.width);
            list.setFixedCellHeight(preferredSize.height);


          JScrollPane scrollPane =
            ScrollPaneFactory.createScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
          scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

            ActionManager actionManager = ActionManager.getInstance();
            ActionGroup actionGroup = (ActionGroup) actionManager.getAction(ThumbnailViewActions.GROUP_TOOLBAR);
            ActionToolbar actionToolbar = actionManager.createActionToolbar(
                    ThumbnailViewActions.ACTION_PLACE, actionGroup, true
            );
            actionToolbar.setTargetComponent(this);

            JComponent toolbar = actionToolbar.getComponent();

            FocusRequester focusRequester = new FocusRequester();
            toolbar.addMouseListener(focusRequester);
            scrollPane.addMouseListener(focusRequester);

            add(toolbar, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }
    }

    public void refresh() {
        createUI();
        if (list != null) {
            DefaultListModel model = (DefaultListModel) list.getModel();
            model.clear();
            VirtualFile root = thumbnailView.getRoot();
            if (root != null && root.isValid() && root.isDirectory()) {
                Set<VirtualFile> files = findFiles(root.getChildren());
              VirtualFile[] virtualFiles = VfsUtil.toVirtualFileArray(files);
                Arrays.sort(virtualFiles, VIRTUAL_FILE_COMPARATOR);

                model.ensureCapacity(model.size() + virtualFiles.length + 1);
                for (VirtualFile virtualFile : virtualFiles) {
                    model.addElement(virtualFile);
                }
                if (model.size() > 0) {
                    list.setSelectedIndex(0);
                }
            } else {
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

    public void setSelected(VirtualFile file, boolean selected) {
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
        int index = ((DefaultListModel) list.getModel()).indexOf(file);
        return index != -1 && list.isSelectedIndex(index);
    }

    @NotNull
    public VirtualFile[] getSelection() {
        if (list != null) {
            Object[] selectedValues = list.getSelectedValues();
            if (selectedValues != null) {
                VirtualFile[] files = new VirtualFile[selectedValues.length];
                for (int i = 0; i < selectedValues.length; i++) {
                    files[i] = (VirtualFile) selectedValues[i];
                }
                return files;
            }
        }
        return VirtualFile.EMPTY_ARRAY;
    }

    private final class ThumbnailListCellRenderer extends ThumbnailComponent
            implements ListCellRenderer {
        private final ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();

        public Component getListCellRendererComponent(
                JList list, Object value, int index, boolean isSelected, boolean cellHasFocus
        ) {
            if (value instanceof VirtualFile) {
                VirtualFile file = (VirtualFile) value;
                setFileName(file.getName());
                setToolTipText(IfsUtil.getReferencePath(thumbnailView.getProject(), file));
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
                } else {
                    // File rendering
                    setFileSize(file.getLength());
                    try {
                        BufferedImage image = IfsUtil.getImage(file);
                        ImageComponent imageComponent = getImageComponent();
                        imageComponent.getDocument().setValue(image);
                        setFormat(IfsUtil.getFormat(file));
                    } catch (Exception e) {
                        // Ignore
                        ImageComponent imageComponent = getImageComponent();
                        imageComponent.getDocument().setValue(null);
                    }
                }

            } else {
                ImageComponent imageComponent = getImageComponent();
                imageComponent.getDocument().setValue(null);
                setFileName(null);
                setFileSize(0);
                setToolTipText(null);
            }

            if (isSelected) {
                setForeground(list.getSelectionForeground());
                setBackground(list.getSelectionBackground());
            } else {
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
                    } else if (isImagesInDirectory(file)) {
                        files.add(file);
                    }
                } else if (typeManager.isImage(file)) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private boolean isImagesInDirectory(VirtualFile dir) {
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

    private final class ThumbnailsMouseAdapter extends MouseAdapter implements MouseMotionListener {
        public void mouseDragged(MouseEvent e) {
            Point point = e.getPoint();
            int index = list.locationToIndex(point);
            if (index != -1) {
                Rectangle cellBounds = list.getCellBounds(index, index);
                if (!cellBounds.contains(point) &&
                        (KeyEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != KeyEvent.CTRL_DOWN_MASK) {
                    list.clearSelection();
                    e.consume();
                }
            }
        }

        public void mouseMoved(MouseEvent e) {
        }


        public void mousePressed(MouseEvent e) {
            Point point = e.getPoint();
            int index = list.locationToIndex(point);
            if (index != -1) {
                Rectangle cellBounds = list.getCellBounds(index, index);
                if (!cellBounds.contains(point) && (KeyEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != KeyEvent.CTRL_DOWN_MASK) {
                    list.clearSelection();
                    e.consume();
                }
            }
        }

        public void mouseClicked(MouseEvent e) {
            Point point = e.getPoint();
            int index = list.locationToIndex(point);
            if (index != -1) {
                Rectangle cellBounds = list.getCellBounds(index, index);
                if (!cellBounds.contains(point) && (KeyEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != KeyEvent.CTRL_DOWN_MASK) {
                    index = -1;
                    list.clearSelection();
                }
            }
            if (index != -1) {
                if (MouseEvent.BUTTON1 == e.getButton() && e.getClickCount() == 2) {
                    // Double click
                    list.setSelectedIndex(index);
                    VirtualFile selected = (VirtualFile) list.getSelectedValue();
                    if (selected != null) {
                        if (selected.isDirectory()) {
                            thumbnailView.setRoot(selected);
                        } else {
                            FileEditorManager fileEditorManager = FileEditorManager.getInstance(thumbnailView.getProject());
                            fileEditorManager.openFile(selected, true);
                        }
                        e.consume();
                    }
                }
                if (MouseEvent.BUTTON3 == e.getButton() && e.getClickCount() == 1) {
                    // Ensure that we have selection
                    if ((KeyEvent.CTRL_DOWN_MASK & e.getModifiersEx()) != KeyEvent.CTRL_DOWN_MASK) {
                        // Ctrl is not pressed
                        list.setSelectedIndex(index);
                    } else {
                        // Ctrl is pressed
                        list.getSelectionModel().addSelectionInterval(index, index);
                    }
                    // Single right click
                    ActionManager actionManager = ActionManager.getInstance();
                    ActionGroup actionGroup = (ActionGroup) actionManager.getAction(ThumbnailViewActions.GROUP_POPUP);
                    ActionPopupMenu menu = actionManager.createActionPopupMenu(ThumbnailViewActions.ACTION_PLACE, actionGroup);
                    JPopupMenu popupMenu = menu.getComponent();
                    popupMenu.pack();
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());

                    e.consume();
                }
            }
        }
    }

    @Nullable
    public Object getData(String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) {
            return thumbnailView.getProject();
        } else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            return selectedFiles.length > 0 ? selectedFiles[0] : null;
        } else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
            return getSelectedFiles();
        } else if (CommonDataKeys.PSI_FILE.is(dataId)) {
            return getData(CommonDataKeys.PSI_ELEMENT.getName());
        } else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            return selectedFiles.length > 0 ? PsiManager.getInstance(thumbnailView.getProject()).findFile(selectedFiles[0]) : null;
        } else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
            return getSelectedElements();
        } else if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            return new ThumbnailNavigatable(selectedFiles.length > 0 ? selectedFiles[0] : null);
        } else if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
            return copyPasteSupport.getCopyProvider();
        } else if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
            return copyPasteSupport.getCutProvider();
        } else if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
            return copyPasteSupport.getPasteProvider();
        } else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
            return deleteProvider;
        } else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            Set<Navigatable> navigatables = new HashSet<>(selectedFiles.length);
            for (VirtualFile selectedFile : selectedFiles) {
                if (!selectedFile.isDirectory()) {
                    navigatables.add(new ThumbnailNavigatable(selectedFile));
                }
            }
            return navigatables.toArray(EMPTY_NAVIGATABLE_ARRAY);
        } else if (ThumbnailView.DATA_KEY.is(dataId)) {
            return thumbnailView;
        } else if (ImageComponentDecorator.DATA_KEY.is(dataId)) {
            return thumbnailView;
        }

        return null;
    }


    @NotNull
    private PsiElement[] getSelectedElements() {
        VirtualFile[] selectedFiles = getSelectedFiles();
        Set<PsiElement> psiElements = new HashSet<>(selectedFiles.length);
        PsiManager psiManager = PsiManager.getInstance(thumbnailView.getProject());
        for (VirtualFile file : selectedFiles) {
            PsiFile psiFile = psiManager.findFile(file);
            PsiElement element = psiFile != null ? psiFile : psiManager.findDirectory(file);
            if (element != null) {
                psiElements.add(element);
            }
        }
      return PsiUtilCore.toPsiElementArray(psiElements);
    }

    @NotNull
    private VirtualFile[] getSelectedFiles() {
        if (list != null) {
            Object[] selectedValues = list.getSelectedValues();
            if (selectedValues != null) {
                VirtualFile[] files = new VirtualFile[selectedValues.length];
                for (int i = 0; i < selectedValues.length; i++) {
                    files[i] = (VirtualFile) selectedValues[i];
                }
                return files;
            }
        }
        return VirtualFile.EMPTY_ARRAY;
    }

    public void dispose() {
        removeAll();

        Options options = OptionsManager.getInstance().getOptions();
        options.removePropertyChangeListener(optionsListener);

        VirtualFileManager.getInstance().removeVirtualFileListener(vfsListener);

        list = null;
        cellRenderer = null;
    }

    private final class ThumbnailNavigatable implements Navigatable {
        private final VirtualFile file;

        public ThumbnailNavigatable(VirtualFile file) {
            this.file = file;
        }

        public void navigate(boolean requestFocus) {
            if (file != null) {
                FileEditorManager manager = FileEditorManager.getInstance(thumbnailView.getProject());
                manager.openFile(file, true);
            }
        }

        public boolean canNavigate() {
            return file != null;
        }

        public boolean canNavigateToSource() {
            return file != null;
        }
    }

    private final class VFSListener extends VirtualFileAdapter {
        public void contentsChanged(@NotNull VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            if (list != null) {
                int index = ((DefaultListModel) list.getModel()).indexOf(file);
                if (index != -1) {
                    Rectangle cellBounds = list.getCellBounds(index, index);
                    list.repaint(cellBounds);
                }
            }
        }

        public void fileDeleted(@NotNull VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            VirtualFile root = thumbnailView.getRoot();
            if (root != null && VfsUtil.isAncestor(file, root, false)) {
                refresh();
            }
            if (list != null) {
                ((DefaultListModel) list.getModel()).removeElement(file);
            }
        }

        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
            refresh();
        }

        public void fileCreated(@NotNull VirtualFileEvent event) {
            refresh();
        }

        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            refresh();
        }
    }

    private final class OptionsChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            Options options = (Options) evt.getSource();
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
        public void mouseClicked(MouseEvent e) {
            requestFocus();
        }
    }
}
