/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor.impl;

import com.intellij.ide.CopyPasteSupport;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PsiActionSupportFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.images.ImagesBundle;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageDocument.ScaledImageProvider;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.options.*;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Locale;

/**
 * Image editor UI
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorUI extends JPanel implements DataProvider, CopyProvider, ImageComponentDecorator, Disposable {
  @NonNls
  private static final String IMAGE_PANEL = "image";
  @NonNls
  private static final String ERROR_PANEL = "error";
  @NonNls
  private static final String ZOOM_FACTOR_PROP = "ImageEditor.zoomFactor";

  @Nullable
  private final ImageEditor editor;
  private final DeleteProvider deleteProvider;
  private final CopyPasteSupport copyPasteSupport;

  private final ImageZoomModel zoomModel = new ImageZoomModelImpl();
  private final ImageWheelAdapter wheelAdapter = new ImageWheelAdapter();
  private final ChangeListener changeListener = new DocumentChangeListener();
  private final ImageComponent imageComponent = new ImageComponent();
  private final JPanel contentPanel;
  private final JLabel infoLabel;

  private final PropertyChangeListener optionsChangeListener = new OptionsChangeListener();
  private final JScrollPane myScrollPane;

  ImageEditorUI(@Nullable ImageEditor editor) {
    this.editor = editor;

    imageComponent.addPropertyChangeListener(ZOOM_FACTOR_PROP, e -> imageComponent.setZoomFactor(getZoomModel().getZoomFactor()));
    Options options = OptionsManager.getInstance().getOptions();
    EditorOptions editorOptions = options.getEditorOptions();
    options.addPropertyChangeListener(optionsChangeListener);

    final PsiActionSupportFactory factory = PsiActionSupportFactory.getInstance();
    if (factory != null && editor != null) {
      copyPasteSupport =
        factory.createPsiBasedCopyPasteSupport(editor.getProject(), this, new PsiActionSupportFactory.PsiElementSelector() {
          public PsiElement[] getSelectedElements() {
            PsiElement[] data = LangDataKeys.PSI_ELEMENT_ARRAY.getData(ImageEditorUI.this);
            return data == null ? PsiElement.EMPTY_ARRAY : data;
          }
        });
    } else {
      copyPasteSupport = null;
    }

    deleteProvider = factory == null ? null : factory.createPsiBasedDeleteProvider();

    ImageDocument document = imageComponent.getDocument();
    document.addChangeListener(changeListener);

    // Set options
    TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
    GridOptions gridOptions = editorOptions.getGridOptions();
    imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
    imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
    imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
    imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
    imageComponent.setGridLineSpan(gridOptions.getLineSpan());
    imageComponent.setGridLineColor(gridOptions.getLineColor());

    // Create layout
    ImageContainerPane view = new ImageContainerPane(imageComponent);
    view.addMouseListener(new EditorMouseAdapter());
    view.addMouseListener(new FocusRequester());

    myScrollPane = ScrollPaneFactory.createScrollPane(view);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    // Zoom by wheel listener
    myScrollPane.addMouseWheelListener(wheelAdapter);

    // Construct UI
    setLayout(new BorderLayout());

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(
      ImageEditorActions.ACTION_PLACE, actionGroup, true
    );
    
    // Make sure toolbar is 'ready' before it's added to component hierarchy 
    // to prevent ActionToolbarImpl.updateActionsImpl(boolean, boolean) from increasing popup size unnecessarily
    actionToolbar.updateActionsImmediately();
    
    actionToolbar.setTargetComponent(this);

    JComponent toolbarPanel = actionToolbar.getComponent();
    toolbarPanel.addMouseListener(new FocusRequester());

    JLabel errorLabel = new JLabel(
      ImagesBundle.message("error.broken.image.file.format"),
      Messages.getErrorIcon(), SwingConstants.CENTER
    );

    JPanel errorPanel = new JPanel(new BorderLayout());
    errorPanel.add(errorLabel, BorderLayout.CENTER);

    contentPanel = new JPanel(new CardLayout());
    contentPanel.add(myScrollPane, IMAGE_PANEL);
    contentPanel.add(errorPanel, ERROR_PANEL);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(toolbarPanel, BorderLayout.WEST);
    infoLabel = new JLabel((String)null, SwingConstants.RIGHT);
    infoLabel.setBorder(JBUI.Borders.emptyRight(2));
    topPanel.add(infoLabel, BorderLayout.EAST);

    add(topPanel, BorderLayout.NORTH);
    add(contentPanel, BorderLayout.CENTER);

    updateInfo();
  }

  private void updateInfo() {
    ImageDocument document = imageComponent.getDocument();
    BufferedImage image = document.getValue();
    if (image != null) {
      ColorModel colorModel = image.getColorModel();
      String format = document.getFormat();
      if (format == null) {
        format = editor != null ? ImagesBundle.message("unknown.format") : "";
      } else {
        format = format.toUpperCase(Locale.ENGLISH);
      }
      VirtualFile file = editor != null ? editor.getFile() : null;
      infoLabel.setText(
        ImagesBundle.message("image.info",
                             image.getWidth(), image.getHeight(), format,
                             colorModel.getPixelSize(), file != null ? StringUtil.formatFileSize(file.getLength()) : ""));
    } else {
      infoLabel.setText(null);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  JComponent getContentComponent() {
    return contentPanel;
  }

  ImageComponent getImageComponent() {
    return imageComponent;
  }

  public void dispose() {
    Options options = OptionsManager.getInstance().getOptions();
    options.removePropertyChangeListener(optionsChangeListener);

    imageComponent.removeMouseWheelListener(wheelAdapter);
    imageComponent.getDocument().removeChangeListener(changeListener);

    removeAll();
  }
  @Override
  public void setTransparencyChessboardVisible(boolean visible) {
    imageComponent.setTransparencyChessboardVisible(visible);
  }

  @Override
  public boolean isTransparencyChessboardVisible() {
    return imageComponent.isTransparencyChessboardVisible();
  }

  @Override
  public boolean isEnabledForActionPlace(String place) {
    // Disable for thumbnails action
    return !ThumbnailViewActions.ACTION_PLACE.equals(place);
  }


  @Override
  public void setGridVisible(boolean visible) {
    imageComponent.setGridVisible(visible);
  }

  @Override
  public boolean isGridVisible() {
    return imageComponent.isGridVisible();
  }

  public ImageZoomModel getZoomModel() {
    return zoomModel;
  }

  public void setImageProvider(ScaledImageProvider imageProvider, String format) {
    ImageDocument document = imageComponent.getDocument();
    BufferedImage previousImage = document.getValue();
    document.setValue(imageProvider);
    if (imageProvider == null) return;
    document.setFormat(format);
    ImageZoomModel zoomModel = getZoomModel();
    if (previousImage == null || !zoomModel.isZoomLevelChanged()) {
      // Set smart zooming behaviour on open
      Options options = OptionsManager.getInstance().getOptions();
      ZoomOptions zoomOptions = options.getEditorOptions().getZoomOptions();
      // Open as actual size
      zoomModel.setZoomFactor(1.0d);

      if (zoomOptions.isSmartZooming()) {
        BufferedImage image = imageProvider.apply(zoomModel.getZoomFactor());
        Dimension prefferedSize = zoomOptions.getPrefferedSize();
        if (prefferedSize.width > image.getWidth() && prefferedSize.height > image.getHeight()) {
          // Resize to preffered size
          // Calculate zoom factor

          double factor =
            (prefferedSize.getWidth() / (double)image.getWidth() + prefferedSize.getHeight() / (double)image.getHeight()) / 2.0d;
          zoomModel.setZoomFactor(Math.ceil(factor));
        }
      }
    }
  }

  private final class ImageContainerPane extends JBLayeredPane {
    private final ImageComponent imageComponent;

    public ImageContainerPane(final ImageComponent imageComponent) {
      this.imageComponent = imageComponent;
      add(imageComponent);

      putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, new Magnificator() {
        @Override
        public Point magnify(double scale, Point at) {
          Point locationBefore = imageComponent.getLocation();
          ImageZoomModel model = editor != null ? editor.getZoomModel() : getZoomModel();
          double factor = model.getZoomFactor();
          model.setZoomFactor(scale * factor);
          return new Point(((int)((at.x - Math.max(scale > 1.0 ? locationBefore.x : 0, 0)) * scale)), 
                           ((int)((at.y - Math.max(scale > 1.0 ? locationBefore.y : 0, 0)) * scale)));
        }
      });
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = imageComponent.getLocation();
      point.x = (bounds.width - imageComponent.getWidth()) / 2;
      point.y = (bounds.height - imageComponent.getHeight()) / 2;
      imageComponent.setLocation(point);
    }

    public void invalidate() {
      centerComponents();
      super.invalidate();
    }

    public Dimension getPreferredSize() {
      return imageComponent.getSize();
    }

    @Override
    protected void paintComponent(@NotNull Graphics g) {
      super.paintComponent(g);
      if (UIUtil.isUnderDarcula()) {
        g.setColor(UIUtil.getControlColor().brighter());
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    }
  }

  private final class ImageWheelAdapter implements MouseWheelListener {
    public void mouseWheelMoved(MouseWheelEvent e) {
      Options options = OptionsManager.getInstance().getOptions();
      EditorOptions editorOptions = options.getEditorOptions();
      ZoomOptions zoomOptions = editorOptions.getZoomOptions();
      if (zoomOptions.isWheelZooming() && e.isControlDown()) {
        int rotation = e.getWheelRotation();
        double oldZoomFactor = zoomModel.getZoomFactor();
        Point oldPosition = myScrollPane.getViewport().getViewPosition();

        if (rotation < 0) {
          zoomModel.zoomOut();
        }
        else if (rotation > 0) {
          zoomModel.zoomIn();
        }

        // reset view, otherwise view size is not obtained correctly sometimes
        Component view = myScrollPane.getViewport().getView();
        myScrollPane.setViewport(null);
        myScrollPane.setViewportView(view);

        if (oldZoomFactor > 0 && rotation != 0) {
          Point mousePoint = e.getPoint();
          double zoomChange = zoomModel.getZoomFactor() / oldZoomFactor;
          Point newPosition = new Point((int)Math.max(0, (oldPosition.getX() + mousePoint.getX()) * zoomChange - mousePoint.getX()),
                                        (int)Math.max(0, (oldPosition.getY() + mousePoint.getY()) * zoomChange - mousePoint.getY()));
          myScrollPane.getViewport().setViewPosition(newPosition);
        }

        e.consume();
      }
    }
  }

  private class ImageZoomModelImpl implements ImageZoomModel {
    private boolean myZoomLevelChanged = false;

    public double getZoomFactor() {
      Dimension size = imageComponent.getCanvasSize();
      BufferedImage image = imageComponent.getDocument().getValue();
      return image != null ? size.getWidth() / (double)image.getWidth() : 1.0d;
    }

    public void setZoomFactor(double zoomFactor) {
      double oldZoomFactor = getZoomFactor();

      if (Double.compare(oldZoomFactor, zoomFactor) == 0) return;

      // Change current size
      Dimension size = imageComponent.getCanvasSize();
      BufferedImage image = imageComponent.getDocument().getValue();
      if (image != null) {
        size.setSize((double)image.getWidth() * zoomFactor, (double)image.getHeight() * zoomFactor);
        imageComponent.setCanvasSize(size);
      }

      revalidate();
      repaint();
      myZoomLevelChanged = false;

      imageComponent.firePropertyChange(ZOOM_FACTOR_PROP, oldZoomFactor, zoomFactor);
    }

    private double getMinimumZoomFactor() {
      BufferedImage image = imageComponent.getDocument().getValue();
      return image != null ? 1.0d / image.getWidth() : 0.0d;
    }

    public void zoomOut() {
      double factor = getZoomFactor();
      if (factor > 1.0d) {
        // Macro
        setZoomFactor(factor / 2.0d);
      } else {
        // Micro
        double minFactor = getMinimumZoomFactor();
        double stepSize = (1.0d - minFactor) / MICRO_ZOOM_LIMIT;
        int step = (int)Math.ceil((1.0d - factor) / stepSize);

        setZoomFactor(1.0d - stepSize * (step + 1));
      }
      myZoomLevelChanged = true;
    }

    public void zoomIn() {
      double factor = getZoomFactor();
      if (factor >= 1.0d) {
        // Macro
        setZoomFactor(factor * 2.0d);
      } else {
        // Micro
        double minFactor = getMinimumZoomFactor();
        double stepSize = (1.0d - minFactor) / MICRO_ZOOM_LIMIT;
        double step = (1.0d - factor) / stepSize;

        setZoomFactor(1.0d - stepSize * (step - 1));
      }
      myZoomLevelChanged = true;
    }

    public boolean canZoomOut() {
      double factor = getZoomFactor();
      double minFactor = getMinimumZoomFactor();
      double stepSize = (1.0 - minFactor) / MICRO_ZOOM_LIMIT;
      double step = Math.ceil((1.0 - factor) / stepSize);

      return step < MICRO_ZOOM_LIMIT;
    }

    public boolean canZoomIn() {
      double zoomFactor = getZoomFactor();
      return zoomFactor < MACRO_ZOOM_LIMIT;
    }

    public boolean isZoomLevelChanged() {
      return myZoomLevelChanged;
    }
  }

  private class DocumentChangeListener implements ChangeListener {
    public void stateChanged(@NotNull ChangeEvent e) {
      ImageDocument document = imageComponent.getDocument();
      BufferedImage value = document.getValue();

      CardLayout layout = (CardLayout)contentPanel.getLayout();
      layout.show(contentPanel, value != null ? IMAGE_PANEL : ERROR_PANEL);

      updateInfo();

      revalidate();
      repaint();
    }
  }

  private class FocusRequester extends MouseAdapter {
    public void mousePressed(@NotNull MouseEvent e) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(ImageEditorUI.this, true);
      });
    }
  }

  private static final class EditorMouseAdapter extends PopupHandler {
    @Override
    public void invokePopup(Component comp, int x, int y) {
      // Single right click
      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_POPUP);
      ActionPopupMenu menu = actionManager.createActionPopupMenu(ImageEditorActions.ACTION_PLACE, actionGroup);
      JPopupMenu popupMenu = menu.getComponent();
      popupMenu.pack();
      popupMenu.show(comp, x, y);
    }
  }


  @Nullable
  public Object getData(String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return editor != null ? editor.getProject() : null;
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return editor != null ? editor.getFile() : null;
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return editor != null ? new VirtualFile[]{editor.getFile()} : VirtualFile.EMPTY_ARRAY;
    }
    else if (CommonDataKeys.PSI_FILE.is(dataId)) {
      return findPsiFile();
    }
    else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return findPsiFile();
    }
    else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      PsiElement psi = findPsiFile();
      return psi != null ? new PsiElement[]{psi} : PsiElement.EMPTY_ARRAY;
    }
    else if (PlatformDataKeys.COPY_PROVIDER.is(dataId) && copyPasteSupport != null) {
      return this;
    }
    else if (PlatformDataKeys.CUT_PROVIDER.is(dataId) && copyPasteSupport != null) {
      return copyPasteSupport.getCutProvider();
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return deleteProvider;
    }
    else if (ImageComponentDecorator.DATA_KEY.is(dataId)) {
      return editor != null ? editor : this;
    }

    return null;
  }

  @Nullable
  private PsiFile findPsiFile() {
    VirtualFile file = editor != null ? editor.getFile() : null;
    return file != null && file.isValid() ? PsiManager.getInstance(editor.getProject()).findFile(file) : null;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    ImageDocument document = imageComponent.getDocument();
    BufferedImage image = document.getValue();
    CopyPasteManager.getInstance().setContents(new ImageTransferable(image));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  private static class ImageTransferable implements Transferable {
    private final BufferedImage myImage;

    public ImageTransferable(@NotNull BufferedImage image) {
      myImage = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor dataFlavor) {
      return DataFlavor.imageFlavor.equals(dataFlavor);
    }

    @Override
    public Object getTransferData(DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
      if (!DataFlavor.imageFlavor.equals(dataFlavor)) {
        throw new UnsupportedFlavorException(dataFlavor);
      }
      return myImage;
    }
  }

  private class OptionsChangeListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      Options options = (Options) evt.getSource();
      EditorOptions editorOptions = options.getEditorOptions();
      TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
      GridOptions gridOptions = editorOptions.getGridOptions();

      imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
      imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
      imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
      imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
      imageComponent.setGridLineSpan(gridOptions.getLineSpan());
      imageComponent.setGridLineColor(gridOptions.getLineColor());
    }
  }

}
