package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author cdr
*/
class ChangeFileEncodingTo extends AnAction {
  private final VirtualFile myFile;
  private final Charset myCharset;

  ChangeFileEncodingTo(@Nullable VirtualFile file, @NotNull Charset charset) {
    super(charset.toString(),  "Change " + (file == null ? "default" : "file '"+file.getName()+"'") +
                               " encoding to '"+charset.name()+"'.", null);
    myFile = file;
    myCharset = charset;
  }

  public void actionPerformed(final AnActionEvent e) {
    chosen(myFile, myCharset);
  }

  protected void chosen(final VirtualFile file, final Charset charset) {
    EncodingManager.getInstance().setEncoding(file, charset);
  }
}
