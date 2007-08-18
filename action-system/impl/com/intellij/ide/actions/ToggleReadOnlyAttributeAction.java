/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;
import java.util.ArrayList;

public class ToggleReadOnlyAttributeAction extends AnAction{
  static VirtualFile[] getFiles(DataContext dataContext){
    ArrayList<VirtualFile> filesList = new ArrayList<VirtualFile>();
    VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    for(int i=0;files!=null&&i<files.length;i++){
      VirtualFile file=files[i];
      if(file.isInLocalFileSystem()){
        filesList.add(file);
      }
    }
    return filesList.toArray(new VirtualFile[filesList.size()]);
  }

  public void update(AnActionEvent e){
    VirtualFile[] files=getFiles(e.getDataContext());
    e.getPresentation().setEnabled(files.length>0);
  }

  public void actionPerformed(final AnActionEvent e){
    ApplicationManager.getApplication().runWriteAction(
      new Runnable(){
        public void run(){
          // Save all documents. We won't be able to save changes to the files that became read-only afterwards.
          FileDocumentManager.getInstance().saveAllDocuments();

          try{
            VirtualFile[] files=getFiles(e.getDataContext());
            for(int i=0;i<files.length;i++){
              VirtualFile file=files[i];
              ReadOnlyAttributeUtil.setReadOnlyAttribute(file,file.isWritable());
            }
          }catch(IOException exc){
            Project project = DataKeys.PROJECT.getData(e.getDataContext());
            Messages.showMessageDialog(
              project,
              exc.getMessage(),
              CommonBundle.getErrorTitle(),Messages.getErrorIcon()
            );
          }
        }
      }
    );
  }
}
