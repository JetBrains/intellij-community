package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Defines save dialog behaviour
 *
 * @author Konstantin Bulenkov
 * @see FileSaverDialog
 * @see FileChooserDescriptor
 * @since 9.0
 */
public class FileSaverDescriptor extends FileChooserDescriptor implements Cloneable {
  private final List<String> extentions;

  /**
   * Constructs save dialog properties
   *
   * @param title save dialog text title (not window title)
   * @param description description
   * @param extentions accepted file extentions: "txt", "jpg", etc. Accept all if empty
   */
  public FileSaverDescriptor(@NotNull String title, @NotNull String description, String... extentions) {
    super(true, true, true, true, false, false);
    setTitle(title);
    setDescription(description);
    this.extentions = Arrays.asList(extentions);
  }

  @Override
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    return extentions.isEmpty() || file.isDirectory() ?
           super.isFileVisible(file, showHiddenFiles)
           :
           extentions.contains(file.getExtension());
  }

  /**
   * Returns accepted file extentions
   *
   * @return accepted file extentions
   */
  public String[] getFileExtentions() {
    return extentions.toArray(new String[extentions.size()]);
  }
}
