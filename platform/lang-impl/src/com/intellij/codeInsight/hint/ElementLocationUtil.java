package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.List;

public class ElementLocationUtil {
  public static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");//TODO: Move to a more proper place

  private ElementLocationUtil() {
  }

  public static void customizeElementLabel(final PsiElement element, final JLabel label) {
    if (element != null) {
      PsiFile file = element.getContainingFile();
      VirtualFile vfile = file == null ? null : file.getVirtualFile();

      if (vfile == null) {
        label.setText("");
        label.setIcon(null);

        return;
      }

      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
      final Module module = fileIndex.getModuleForFile(vfile);

      if (module != null) {
        label.setText(module.getName());
        label.setIcon(module.getModuleType().getNodeIcon(false));
      }
      else {
        final List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(vfile);

        OrderEntry entry = null;

        for (OrderEntry order : entries) {
          if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
            entry = order;
            break;
          }
        }

        if (entry != null) {
          label.setText(entry.getPresentableName());
          label.setIcon(LIB_ICON_CLOSED);
        }
      }
    }
  }
}
