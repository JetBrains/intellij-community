package com.intellij.database.vfs.fragment;

import com.intellij.database.DataGridBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class TableDataFragmentFile extends LightVirtualFileBase {
  private final TextRange myRange;

  protected TableDataFragmentFile(@NotNull VirtualFile originalFile, @NotNull TextRange range) {
    super(originalFile.getName() + " (fragment)", MyFileType.INSTANCE, 0);
    setOriginalFile(originalFile);
    myRange = range;
  }

  @Override
  public boolean isValid() {
    return getOriginalFile().isValid();
  }

  public @NotNull TextRange getRange() {
    return myRange;
  }

  @Override
  public @NotNull FileType getFileType() {
    return MyFileType.INSTANCE;
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw throwNotSupported();
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    throw throwNotSupported();
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  private static @NotNull IOException throwNotSupported() {
    return new IOException("Not supported");
  }

  public static final class MyFileType implements FileType {
    public static final MyFileType INSTANCE = new MyFileType();

    private MyFileType() {
    }

    @Override
    public @NotNull String getName() {
      return "Data Fragment";
    }

    @Override
    public @NotNull String getDescription() {
      return DataGridBundle.message("filetype.fragment.file.interpretable.as.table.description");
    }

    @Override
    public @NotNull String getDefaultExtension() {
      return "";
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.DataTables;
    }

    @Override
    public boolean isBinary() {
      return false;
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }
  }
}
