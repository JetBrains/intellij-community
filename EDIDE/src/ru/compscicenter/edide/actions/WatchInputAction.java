package ru.compscicenter.edide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import icons.StudyIcons;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.UserTest;
import ru.compscicenter.edide.editor.StudyEditor;
import ru.compscicenter.edide.ui.TestContentPanel;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WatchInputAction extends DumbAwareAction {

  public static final String TEST_TAB_NAME = "test";
  public static final String USER_TEST_INPUT = "userTestInput";
  public static final String USER_TEST_OUTPUT = "userTestOutput";
  private JBEditorTabs tabbedPane;
  private Map<TabInfo, UserTest> myTabs = new HashMap<TabInfo, UserTest>();

  public void showInput(final Project project) {
    final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      assert openedFile != null;
      TaskFile taskFile = studyTaskManager.getTaskFile(openedFile);
      assert taskFile != null;
      final Task currentTask = taskFile.getTask();
      tabbedPane = new JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
      tabbedPane.addListener(new TabsListener.Adapter(){
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (newSelection.getIcon() != null) {
            int tabCount = tabbedPane.getTabCount();
            UserTest userTest = createUserTest(currentTask);
            TestContentPanel testContentPanel = new TestContentPanel(userTest);
            TabInfo testTab = AddTestTab(tabbedPane.getTabCount(), testContentPanel, currentTask);
            myTabs.put(testTab, userTest);
            tabbedPane.addTabSilently(testTab, tabCount - 1);
            tabbedPane.select(testTab, true);
          }
        }
      });
      List<UserTest> userTests = currentTask.getUserTests();
      int i = 1;
      for (UserTest userTest : userTests) {
        String inputFileText = currentTask.getResourceText(project, userTest.getInput(), false);
        String outputFileText = currentTask.getResourceText(project, userTest.getOutput(), false);
        TestContentPanel myContentPanel = new TestContentPanel(userTest);
        myContentPanel.addInputContent(inputFileText);
        myContentPanel.addOutputContent(outputFileText);
        TabInfo testTab = AddTestTab(i, myContentPanel, currentTask);
        myTabs.put(testTab, userTest);
        tabbedPane.addTabSilently(testTab, i - 1);
        i++;
      }
      TabInfo plusTab = new TabInfo(new JPanel());
      plusTab.setIcon(StudyIcons.Add);
      tabbedPane.addTabSilently(plusTab, tabbedPane.getTabCount());
      final JBPopup hint =
        JBPopupFactory.getInstance().createComponentPopupBuilder(tabbedPane.getComponent(), tabbedPane.getComponent())
          .setResizable(true)
          .setMovable(true)
          .setRequestFocus(true)
          .createPopup();
      StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
      assert selectedStudyEditor != null;
      hint.showInCenterOf(selectedStudyEditor.getComponent());
      hint.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          for (final UserTest userTest : currentTask.getUserTests()) {

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                String inputFile = userTest.getInput();
                String outputFile = userTest.getOutput();
                File inputRealFile = new File(currentTask.createResourceFile(project, inputFile).getPath());
                File outputRealFile = new File(currentTask.createResourceFile(project, outputFile).getPath());
                try {
                  PrintWriter printWriter = new PrintWriter(new FileOutputStream(inputRealFile));
                  printWriter.println(userTest.getInputBuffer().toString());
                  printWriter.close();
                  PrintWriter printWriter2 = new PrintWriter(new FileOutputStream(outputRealFile));
                  printWriter2.println(userTest.getOutputBuffer().toString());
                  printWriter2.close();
                }
                catch (FileNotFoundException e) {
                  e.printStackTrace();
                }

              }
            });
                      }
        }
      });
    }
  }

  private UserTest createUserTest(Task currentTask) {
    UserTest userTest = new UserTest();
    List<UserTest> userTests = currentTask.getUserTests();
    int testNum = userTests.size();
    String inputName = USER_TEST_INPUT + testNum;
    String outputName = USER_TEST_OUTPUT + testNum;
    userTest.setInput(inputName);
    userTest.setOutput(outputName);
    userTests.add(userTest);
    return userTest;
  }

  private TabInfo AddTestTab(int nameIndex, TestContentPanel contentPanel, Task currentTask) {
    return createClosableTab(contentPanel, currentTask).setText(TEST_TAB_NAME + String.valueOf(nameIndex));
  }

  private TabInfo createClosableTab(TestContentPanel contentPanel, Task currentTask) {
    TabInfo closableTab = new TabInfo(contentPanel);
    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(contentPanel, closableTab, currentTask));
    closableTab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    return closableTab;
  }

  public void actionPerformed(AnActionEvent e) {
    showInput(e.getProject());
  }


  private class CloseTab extends AnAction implements DumbAware {

    ShadowAction myShadow;
    private final TabInfo myTabInfo;
    private Task myTask;

    public CloseTab(JComponent c, TabInfo info, Task task) {
      myTabInfo = info;
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE), c);
      myTask = task;
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(tabbedPane.isEditorTabs() ? AllIcons.Actions.CloseNew : AllIcons.Actions.Close);
      e.getPresentation().setHoveredIcon(tabbedPane.isEditorTabs() ? AllIcons.Actions.CloseNewHovered : AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().SHOW_CLOSE_BUTTON);
      e.getPresentation().setText("Delete test");
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      tabbedPane.removeTab(myTabInfo);
      UserTest userTest = myTabs.get(myTabInfo);
      myTask.getUserTests().remove(userTest);
    }
  }
}
