package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * author: lesya
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class VirtualFileInfoAction extends AnAction{

  public static final DateFormat DATE_FORMAT =
    SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);


  public void actionPerformed(AnActionEvent e) {
    String pathToFile = Messages.showInputDialog("Path to file: ",
                                 "Virtual File Info",
                                 Messages.getQuestionIcon());
    if (pathToFile == null) return;
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(pathToFile));
    if (virtualFile == null){
      Messages.showErrorDialog("Cannot find virtual file", "Virtual File Info");
      return;
    } else {
      StringBuffer info = new StringBuffer();
      info.append("Path: ");
      info.append(virtualFile.getPath());
      info.append("\n");
      info.append("Time stamp: ");
      info.append(DATE_FORMAT.format(new Date(virtualFile.getTimeStamp())));
      info.append("\n");
      info.append("isValid: ");
      info.append(String.valueOf(virtualFile.isValid()));
      info.append("\n");
      info.append("isWritable: ");
      info.append(String.valueOf(virtualFile.isWritable()));
      info.append("\n");
      info.append("Content: ");
      try {
        info.append(VfsUtil.loadText(virtualFile));
      }
      catch (IOException e1) {
        info.append("<unable to load content>");
        info.append(e1.getMessage());
      }
      info.append("\n");

      Messages.showMessageDialog(info.toString(), "Virtual File Info", Messages.getInformationIcon());
    }
  }


}
