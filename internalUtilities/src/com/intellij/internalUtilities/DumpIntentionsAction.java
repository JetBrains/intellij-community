/*
 * User: anna
 * Date: 28-Jun-2007
 */
package com.intellij.internalUtilities;

import com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DumpIntentionsAction extends AnAction {
  public DumpIntentionsAction() {
    super("Dupm Intentions");
  }

  public void actionPerformed(AnActionEvent e) {
    final VirtualFile[] files =
      FileChooser.chooseFiles(e.getData(DataKeys.PROJECT), FileChooserDescriptorFactory.createSingleFolderDescriptor());
    if (files.length > 0) {
      final List<IntentionActionMetaData> list = IntentionManagerSettings.getInstance().getMetaData();
      final File root = VfsUtil.virtualToIoFile(files[0]);
      for (IntentionActionMetaData metaData : list) {
        File dir = root;
        for (String rec : metaData.myCategory) {
          dir = new File(dir, rec);
          dir.mkdir();
        }
        dir = new File(dir, metaData.myFamily);

        try {
          FileUtil.copyDir(VfsUtil.virtualToIoFile(VfsUtil.findFileByURL(metaData.getDirURL())), dir);
        }
        catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    }
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(DataKeys.PROJECT) != null);
  }
}