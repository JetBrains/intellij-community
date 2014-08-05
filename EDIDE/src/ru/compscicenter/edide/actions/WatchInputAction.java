package ru.compscicenter.edide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
import java.util.List;

public class WatchInputAction extends DumbAwareAction {

  private JBEditorTabs tabbedPane;

  public void showInput(Project project) {
    final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      assert openedFile != null;
      TaskFile taskFile = studyTaskManager.getTaskFile(openedFile);
      assert taskFile != null;
      Task currentTask = taskFile.getTask();
      tabbedPane = new JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
      tabbedPane.addListener(new TabsListener.Adapter(){
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (newSelection.getIcon() != null) {
            int tabCount = tabbedPane.getTabCount();
            TestContentPanel testContentPanel = new TestContentPanel();
            TabInfo testTab = AddTestTab(tabbedPane.getTabCount(), testContentPanel);
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
        TestContentPanel myContentPanel = new TestContentPanel();
        myContentPanel.addInputContent(inputFileText);
        myContentPanel.addOutputContent(outputFileText);
        TabInfo testTab = AddTestTab(i, myContentPanel);
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
    }
  }

  private TabInfo AddTestTab(int nameIndex, TestContentPanel contentPanel) {
    return createClosableTab(contentPanel).setText("test" + String.valueOf(nameIndex));
  }

  private TabInfo createClosableTab(TestContentPanel contentPanel) {
    TabInfo closableTab = new TabInfo(contentPanel);
    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(contentPanel, closableTab));
    closableTab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    return closableTab;
  }

  public void actionPerformed(AnActionEvent e) {
    showInput(e.getProject());
  }


  private class CloseTab extends AnAction implements DumbAware {

    ShadowAction myShadow;
    private final TabInfo myTabInfo;

    public CloseTab(JComponent c, TabInfo info) {
      myTabInfo = info;
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE), c);
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
    }
  }
}
