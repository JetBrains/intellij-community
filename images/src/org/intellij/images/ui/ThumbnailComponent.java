// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Thumbnail component.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
@Deprecated(forRemoval = true)
public class ThumbnailComponent extends JComponent {
  private static final @NonNls String FORMAT_PROP = "format";
  private static final @NonNls String FILE_SIZE_PROP = "fileSize";
  private static final @NonNls String FILE_NAME_PROP = "fileName";
  private static final @NonNls String DIRECTORY_PROP = "directory";
  private static final @NonNls String IMAGES_COUNT_PROP = "imagesCount";

  /**
   * @see #getUIClassID
   * @see #readObject
   */
  private static final @NonNls String uiClassID = "ThumbnailComponentUI";

  static {
    UIManager.getDefaults().put(uiClassID, ThumbnailComponentUI.class.getName());
  }

  /**
   * Image component for rendering thumbnail image.
   */
  private final ImageComponent imageComponent = new ImageComponent();

  private String format;
  private long fileSize;
  private String fileName;
  private boolean directory;
  private int imagesCount;

  public ThumbnailComponent() {
    updateUI();
  }

  public ImageComponent getImageComponent() {
    return imageComponent;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    String oldValue = this.format;
    if (oldValue != null && !oldValue.equals(format) || oldValue == null && format != null) {
      this.format = format;
      firePropertyChange(FORMAT_PROP, oldValue, this.format);
    }
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    long oldValue = this.fileSize;
    if (oldValue != fileSize) {
      this.fileSize = fileSize;
      firePropertyChange(FILE_SIZE_PROP, Long.valueOf(oldValue), Long.valueOf(this.fileSize));
    }
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    String oldValue = this.fileName;
    if (oldValue != null && !oldValue.equals(fileName) || oldValue == null && fileName != null) {
      this.fileName = fileName;
      firePropertyChange(FILE_NAME_PROP, oldValue, this.fileName);
    }
  }

  public boolean isDirectory() {
    return directory;
  }

  public void setDirectory(boolean directory) {
    boolean oldValue = this.directory;
    if (oldValue != directory) {
      this.directory = directory;
      firePropertyChange(DIRECTORY_PROP, oldValue, this.directory);
    }
  }

  public int getImagesCount() {
    return imagesCount;
  }

  public void setImagesCount(int imagesCount) {
    int oldValue = this.imagesCount;
    if (oldValue != imagesCount) {
      this.imagesCount = imagesCount;
      firePropertyChange(IMAGES_COUNT_PROP, oldValue, this.imagesCount);
    }
  }

  public String getFileSizeText() {
    return StringUtil.formatFileSize(fileSize);
  }

  @Override
  public void updateUI() {
    setUI(UIManager.getUI(this));
  }

  @Override
  public String getUIClassID() {
    return uiClassID;
  }
}