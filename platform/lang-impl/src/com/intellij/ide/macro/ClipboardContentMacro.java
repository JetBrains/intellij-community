package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author nik
 */
public class ClipboardContentMacro extends Macro {
  private static final Logger LOG = Logger.getInstance(ClipboardContentMacro.class);

  @Override
  public String getName() {
    return "ClipboardContent";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.clipboard.content");
  }

  @Nullable
  @Override
  public String expand(DataContext dataContext) throws ExecutionCancelledException {
    Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) return null;

    try {
      return (String)contents.getTransferData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      LOG.info(e);
      return null;
    }
  }
}
