package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class ChangeFileEncodingGroup extends ActionGroup{
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    Collection<Charset> charsets = EncodingManager.getInstance().getFavorites();
    List<AnAction> children = new ArrayList<AnAction>();
    for (Charset charset : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, charset);
      children.add(action);
    }

    children.add(new More(virtualFile));
    children.add(new Separator());
    return children.toArray(new AnAction[children.size()]);
  }

  private static class More extends AnAction {
    private final VirtualFile myVirtualFile;

    private More(VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
      getTemplatePresentation().setText("more...");
    }

    public void actionPerformed(final AnActionEvent e) {
      final DefaultActionGroup group = new DefaultActionGroup();
      Charset[] charsets = CharsetToolkit.getAvailableCharsets();
      for (Charset charset : charsets) {
        group.add(new ChangeFileEncodingTo(myVirtualFile, charset));
      }

      ChooseEncodingDialog dialog = new ChooseEncodingDialog(charsets, myVirtualFile.getCharset(), myVirtualFile);
      dialog.show();
      Charset charset = dialog.getChosen();
      if (dialog.isOK() && charset != null) {
        EncodingManager.getInstance().setEncoding(myVirtualFile, charset);
      }

      //final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, e.getDataContext(),
      //                                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
      //                                                                            null,
      //                                                                            30);
      //popup.showInBestPositionFor(e.getDataContext());
    }

  }
}
