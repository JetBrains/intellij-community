package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import ru.compscicenter.edide.StudyTaskManager;

/**
* User: lia
* Date: 23.05.14
* Time: 20:33
*/
class CheckAction extends AnAction {

  @Override
  public boolean displayTextInToolbar() {
    return false;
  }
  @Override
  public void actionPerformed(AnActionEvent e) {
      StudyTaskManager.getInstance(e.getProject()).getSelectedWindow().setResolveStatus(true);
      StudyTaskManager.getInstance(e.getProject()).setSelectedWindow(null);
//    Project project = e.getProject();
//    FileDocumentManager.getInstance().saveAllDocuments();
//    TaskManager tm = TaskManager.getInstance(project);
//    if (!(project != null && project.isOpen())) {
//      return;
//    }
//    String basePath = project.getBasePath();
//    if (basePath == null) return;
//    Editor editor = StudyEditor.getRecentOpenedEditor(project);
//    if (editor == null) {
//      return;
//    }
//    VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
//    //TODO: replace with platform independent path join
//    String testFile = basePath +
//                      "/.idea/" + tm.getTest(tm.getTaskNumForFile(vfOpenedFile != null ? vfOpenedFile.getName() : null));
//    GeneralCommandLine cmd = new GeneralCommandLine();
//    cmd.setWorkDirectory(basePath + "/.idea");
//    cmd.setExePath("python");
//    cmd.addParameter(testFile);
//    try {
//      Process p = cmd.createProcess();
//      InputStream is_err = p.getErrorStream();
//      InputStream is = p.getInputStream();
//      BufferedReader bf = new BufferedReader(new InputStreamReader(is));
//      BufferedReader bf_err = new BufferedReader(new InputStreamReader(is_err));
//      String line;
//      String testResult = "test failed";
//      while ((line = bf.readLine()) != null) {
//        if (line.equals("OK")) {
//          testResult = "test passed";
//        }
//        System.out.println(line);
//      }
//      while ((line = bf_err.readLine()) != null) {
//        if (line.equals("OK")) {
//          testResult = "test passed";
//        }
//        System.out.println(line);
//      }
////        BalloonBuilder builder =
////                JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(testResult, MessageType.INFO, null);
////        Balloon balloon = builder.createBalloon();
////        RelativePoint where = new RelativePoint(e.getInputEvent().getComponent(), e.getInputEvent().getComponent().getLocationOnScreen());
////        balloon.show(where, Balloon.Position.above);
//      JOptionPane.showMessageDialog(null, testResult, "", JOptionPane.INFORMATION_MESSAGE);
//    }
//    catch (ExecutionException e1) {
//      e1.printStackTrace();
//    }
//    catch (IOException e1) {
//      e1.printStackTrace();
//    }
  }
}
