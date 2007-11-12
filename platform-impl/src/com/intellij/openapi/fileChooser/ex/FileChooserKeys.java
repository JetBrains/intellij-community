package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author yole
 */
public class FileChooserKeys {
  public static final DataKey<FileType> NEW_FILE_TYPE = DataKey.create("NewFileType");
  public static final DataKey<String> NEW_FILE_TEMPLATE_TEXT = DataKey.create("NewFileTemplateText"); 
}