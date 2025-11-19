// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.ui

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * Thumbnail component.
 * 
 * @author [Alexey Efimov](mailto:aefimov.box@gmail.com)
 */
@Deprecated("Not needed anymore", level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval
open class ThumbnailComponent : JComponent() {

  /**
   * Image component for rendering thumbnail image.
   */
  val imageComponent: ImageComponent = ImageComponent()

  var format: String? = null
    set(format) {
      val oldValue = field
      if (oldValue != null && oldValue != format || oldValue == null && format != null) {
        field = format
        firePropertyChange(FORMAT_PROP, oldValue, field)
      }
    }

  private var fileSize: Long = 0

  var fileName: String? = null
    set(fileName) {
      val oldValue = field
      if (oldValue != null && oldValue != fileName || oldValue == null && fileName != null) {
        field = fileName
        firePropertyChange(FILE_NAME_PROP, oldValue, field)
      }
    }

  var isDirectory: Boolean = false
    set(directory) {
      val oldValue = field
      if (oldValue != directory) {
        field = directory
        firePropertyChange(DIRECTORY_PROP, oldValue, field)
      }
    }

  var imagesCount: Int = 0
    set(imagesCount) {
      val oldValue = field
      if (oldValue != imagesCount) {
        field = imagesCount
        firePropertyChange(IMAGES_COUNT_PROP, oldValue, field)
      }
    }

  init {
    updateUI()
  }

  fun getFileSize(): Long {
    return fileSize
  }

  fun setFileSize(fileSize: Long) {
    val oldValue = this.fileSize
    if (oldValue != fileSize) {
      this.fileSize = fileSize
      firePropertyChange(FILE_SIZE_PROP, oldValue, this.fileSize)
    }
  }

  fun getFileSizeText(): String {
    return StringUtil.formatFileSize(fileSize)
  }

  override fun updateUI() {
    setUI(UIManager.getUI(this))
  }

  override fun getUIClassID(): String {
    return Companion.uiClassID
  }

  companion object {
    @NonNls
    private const val FORMAT_PROP = "format"

    @NonNls
    private const val FILE_SIZE_PROP = "fileSize"

    @NonNls
    private const val FILE_NAME_PROP = "fileName"

    @NonNls
    private const val DIRECTORY_PROP = "directory"

    @NonNls
    private const val IMAGES_COUNT_PROP = "imagesCount"

    /**
     * @see .getUIClassID
     * 
     * @see .readObject
     */
    @NonNls
    private const val uiClassID = "ThumbnailComponentUI"

    init {
      UIManager.getDefaults().put(uiClassID, ThumbnailComponentUI::class.java.getName())
    }
  }
}