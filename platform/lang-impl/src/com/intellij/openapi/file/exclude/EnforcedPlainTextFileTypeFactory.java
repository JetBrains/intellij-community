/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Registers text file type for particular virtual files rather than using .txt extension.
 * @author Rustam Vishnyakov
 */
public class EnforcedPlainTextFileTypeFactory extends FileTypeFactory {

  public static final LayeredIcon ENFORCED_PLAIN_TEXT_ICON = new LayeredIcon(2);

  static {
    ENFORCED_PLAIN_TEXT_ICON.setIcon(IconLoader.getIcon("/fileTypes/text.png"), 0);
    ENFORCED_PLAIN_TEXT_ICON.setIcon(Icons.EXCLUDED_FROM_COMPILE_ICON, 1);
  }

  
  private final FileTypeIdentifiableByVirtualFile myFileType;
  
  
  public EnforcedPlainTextFileTypeFactory() {
    
    
    myFileType = new FileTypeIdentifiableByVirtualFile() {

      @Override
      public boolean isMyFileType(VirtualFile file) {
        if (isMarkedAsPlainText(file)) {
          return true;
        }
        return false;
      }

      @NotNull
      @Override
      public String getName() {
        return "Enforced Plain Text";
      }

      @NotNull
      @Override
      public String getDescription() {
        return "Enforced Plain Text";
      }

      @NotNull
      @Override
      public String getDefaultExtension() {
        return "fakeTxt";
      }

      @Override
      public Icon getIcon() {
        return ENFORCED_PLAIN_TEXT_ICON;
      }

      @Override
      public boolean isBinary() {
        return false;
      }

      @Override
      public boolean isReadOnly() {
        return true;
      }

      @Override
      public String getCharset(@NotNull VirtualFile file, byte[] content) {
        return null;
      }
    };
  }

  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(myFileType, "");
  }
  
  private static boolean isMarkedAsPlainText(VirtualFile file) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    if (typeManager == null) return false;
    return typeManager.isMarkedAsPlainText(file);
  }

}
