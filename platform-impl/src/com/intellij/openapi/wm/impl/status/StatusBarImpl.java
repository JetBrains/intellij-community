package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarCustomComponentFactory;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatusBarImpl extends JPanel implements StatusBarEx {
  private final PositionPanel myPositionPanel = new PositionPanel(this);
  private final ToggleReadOnlyAttributePanel myToggleReadOnlyAttributePanel = new ToggleReadOnlyAttributePanel(this);
  private final EncodingPanel myEncodingPanel = new EncodingPanel(this);
  private final IdeNotificationArea myNotificationArea = new IdeNotificationArea(this);
  private final MemoryUsagePanel myMemoryUsagePanel = new MemoryUsagePanel();
  private final InsertOverwritePanel myInsertOverwritePanel = new InsertOverwritePanel();
  private final IdeMessagePanel myMessagePanel = new IdeMessagePanel(MessagePool.getInstance());
  private final JPanel myCustomIndicationsPanel = new JPanel(new GridBagLayout());
  private final JPanel myAppLevelCustomIndicationsPanel = new JPanel(new GridBagLayout());
  private String myInfo = "";

  private final MyUISettingsListener myUISettingsListener;
  private InfoAndProgressPanel myInfoAndProgressPanel;

  private final UISettings myUISettings;
  private AsyncProcessIcon myRefreshIcon;
  private EmptyIcon myEmptyRefreshIcon;
  private final List<StatusBarPatch> myFileStatusComponents = new ArrayList<StatusBarPatch>();
  private final List<StatusBarPatch> myPatches = new ArrayList<StatusBarPatch>();
  private JPanel myPatchesPanel;
  private List<StatusBarCustomComponentFactory> myCustomComponentsFactoryList;
  private final Map<StatusBarCustomComponentFactory, JComponent> myFactory2Component = new HashMap<StatusBarCustomComponentFactory, JComponent>();

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.status.StatusBarImpl");

  public StatusBarImpl(UISettings uiSettings) {
    myUISettings = uiSettings;
    constructUI();

    myUISettingsListener=new MyUISettingsListener();
  }

  private void constructUI() {
    setLayout(new BorderLayout());
    setOpaque(true);

    myRefreshIcon = new AsyncProcessIcon("Refreshing filesystem") {
      protected Icon getPassiveIcon() {
        return myEmptyRefreshIcon;
      }
    };
    myEmptyRefreshIcon = new EmptyIcon(myRefreshIcon.getPreferredSize().width, myRefreshIcon.getPreferredSize().height);
    myInfoAndProgressPanel = new InfoAndProgressPanel(this);
    setRefreshVisible(false);

    final NonOpaquePanel center = new NonOpaquePanel(new BorderLayout());
    center.add(myRefreshIcon, BorderLayout.WEST);
    center.add(myInfoAndProgressPanel, BorderLayout.CENTER);


    myPatchesPanel = new JPanel(new GridBagLayout());
    myPatchesPanel.setOpaque(false);
    setBorder(new EmptyBorder(2, 0, 1, 0));


    add(center, BorderLayout.CENTER);
    add(myPatchesPanel, BorderLayout.EAST);

    recreatePatches();
  }

  public IdeNotificationArea getNotificationArea() {
    return myNotificationArea;
  }

  private void recreatePatches() {
    myPatchesPanel.removeAll();
    myPatches.clear();

    addPatch(myPositionPanel, true);
    addPatch(myToggleReadOnlyAttributePanel, true);
    addPatch(myInsertOverwritePanel, true);
    addPatch(myEncodingPanel, true);
    for (int i = 0; i < myFileStatusComponents.size(); i++) {
      StatusBarPatch component = myFileStatusComponents.get(i);
      addPatch(component, i==0);
    }

    addPatch(new StatusBarPatch() {
      public JComponent getComponent() {
        return myCustomIndicationsPanel;
      }

      public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
        return componentSelected == null ? null : componentSelected.getToolTipText();
      }

      public void clear() {

      }
    }, true);

    addPatch(new StatusBarPatch() {
      public JComponent getComponent() {
        return myAppLevelCustomIndicationsPanel;
      }

      public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
        return componentSelected == null ? null : componentSelected.getToolTipText();
      }

      public void clear() {

      }
    }, true);

    addPatch(myMessagePanel, false);
    addPatch(myNotificationArea, true);
    addPatch(myMemoryUsagePanel, true);

    final GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.anchor = GridBagConstraints.WEST;

    for (int i = 0; i < myPatches.size(); i++) {
      StatusBarPatch patch = myPatches.get(i);
      JComponent component = patch.getComponent();
      if (i == myPatches.size() - 1) {
        gbConstraints.fill = GridBagConstraints.VERTICAL;
        gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      }
      myPatchesPanel.add(component, gbConstraints);
      gbConstraints.fill = GridBagConstraints.VERTICAL;
      gbConstraints.weightx = 0;
      gbConstraints.gridx++;
    }

  }

  public void setCustomComponentsFactory(final List<StatusBarCustomComponentFactory> customComponentsFactory) {
    disposeAppLevelCustomComponents();
    myCustomComponentsFactoryList = customComponentsFactory;
    for (StatusBarCustomComponentFactory factory : myCustomComponentsFactoryList) {
      JComponent c = factory.createComponent(this);
      if (c != null) {
        addAppLevelCustomIndicationComponent(c);
      }
    }
  }

  private void setRefreshVisible(final boolean visible) {
    if (visible) {
      myRefreshIcon.resume();
    }
    else {
      myRefreshIcon.suspend();
    }
  }

  public void add(final ProgressIndicatorEx indicator, TaskInfo info) {
    myInfoAndProgressPanel.addProgress(indicator, info);
  }

  public void setProcessWindowOpen(final boolean open) {
    myInfoAndProgressPanel.setProcessWindowOpen(open);
  }

  public void update(final Editor editor) {
    if (editor != null) {
      final Document document = editor.getDocument();
      if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;
    }

    for (StatusBarPatch patch : myPatches) {
      try {
        patch.updateStatusBar(editor, null);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void dispose() {
    disposeAppLevelCustomComponents();
  }

  private void disposeAppLevelCustomComponents() {
    if (myCustomComponentsFactoryList != null) {
      for (StatusBarCustomComponentFactory factory : myCustomComponentsFactoryList) {
        try {
          JComponent c = myFactory2Component.get(factory);
          if (c != null) {
            factory.disposeComponent(this, c);
          }
        }
        finally {
          myFactory2Component.remove(factory);
        }

      }
    }
    myCustomComponentsFactoryList = null;
  }

  Editor getEditor() {
    final Project project = getProject();
    if (project == null) return null;
    return getEditor(project);
  }

  private static Editor getEditor(final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }

  public boolean isProcessWindowOpen() {
    return myInfoAndProgressPanel.isProcessWindowOpen();
  }

  protected final void paintComponent(final Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());

    final Color dark = getBackground().darker();

    g.setColor(dark);
    g.drawLine(0, 0, getWidth(), 0);

    final Color lighter = new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), 75);
    g.setColor(lighter);
    g.drawLine(0, 1, getWidth(), 1);

    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
  }

  public final void addNotify() {
    super.addNotify();
    setMemoryIndicatorVisible(myUISettings.SHOW_MEMORY_INDICATOR);
    myUISettings.addUISettingsListener(myUISettingsListener);
  }

  public final void removeNotify() {
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  public final void setInfo(@Nullable final String s) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myInfo = s;
        myInfoAndProgressPanel.setText(s != null ? s : " ");
      }
    });
  }

  public void fireNotificationPopup(@NotNull JComponent content, final Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  public final String getInfo() {
    return myInfo;
  }

  private void addAppLevelCustomIndicationComponent(@NotNull final JComponent c) {
    addCustomIndicationComponent(c, myAppLevelCustomIndicationsPanel);
  }

  public final void addCustomIndicationComponent(@NotNull JComponent c) {

    addCustomIndicationComponent(c, myCustomIndicationsPanel);
  }

  private void addCustomIndicationComponent(final JComponent c, final JPanel indicationsPanel) {
    final GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.insets = new Insets(0, 0, 0, 2);


    indicationsPanel.setVisible(true);
    indicationsPanel.add(c, gbConstraints);
  }

  public void removeCustomIndicationComponent(@NotNull final JComponent component) {
    removeCustomIndicationComponent(component, myCustomIndicationsPanel);
  }

  private void removeCustomIndicationComponent(final JComponent component, final JPanel indicationsPanel) {
    indicationsPanel.remove(component);
    if (indicationsPanel.getComponentCount() == 0) {
      indicationsPanel.setVisible(false);
    }
  }

  /**
   * Clears all sections in status bar
   */
  public final void clear(){
    for (StatusBarPatch patch : myPatches) {
      patch.clear();
    }
  }

  public void addFileStatusComponent(StatusBarPatch component) {
    myFileStatusComponents.add(component);
    recreatePatches();
  }

  public void removeFileStatusComponent(StatusBarPatch component) {
    myFileStatusComponents.remove(component);
    recreatePatches();
  }

  public void cleanupCustomComponents() {
    myCustomIndicationsPanel.removeAll();
  }

  public final Dimension getMinimumSize() {
    final Dimension p = super.getPreferredSize();
    final Dimension m = super.getMinimumSize();
    return new Dimension(m.width, p.height);
  }

  public final Dimension getMaximumSize() {
    final Dimension p = super.getPreferredSize();
    final Dimension m = super.getMaximumSize();
    return new Dimension(m.width, p.height);
  }

  private void setMemoryIndicatorVisible(final boolean state) {
    if (myMemoryUsagePanel != null) {
      myMemoryUsagePanel.setVisible(state);
    }
  }

  private final class MyUISettingsListener implements UISettingsListener{
    public void uiSettingsChanged(final UISettings uiSettings) {
      setMemoryIndicatorVisible(uiSettings.SHOW_MEMORY_INDICATOR);
    }
  }

  public static class SeparatorBorder implements Border {

    private final boolean myLeft;

    public SeparatorBorder(final boolean left) {
      myLeft = left;
    }

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Color bg = c.getBackground();
      g.setColor(bg != null ? bg.darker() : Color.darkGray);
      final int inset = 1;
      if (myLeft) {
        g.drawLine(0, inset, 0, c.getHeight() - inset - 1);
      } else {
        g.drawLine(c.getWidth() - 1, inset, c.getWidth() - 1, c.getHeight() - inset - 1);
      }
    }

    public Insets getBorderInsets(final Component c) {
      return new Insets(0, 1, 0, 1);
    }

    public boolean isBorderOpaque() {
      return false;
    }


    public static class Left extends SeparatorBorder {
      public Left() {
        super(true);
      }
    }
  }

  public void startRefreshIndication(String tooltipText) {
    myRefreshIcon.setToolTipText(tooltipText);
    setRefreshVisible(true);
  }

  public void stopRefreshIndication() {
    setRefreshVisible(false);
  }

  private void addPatch(StatusBarPatch panel, boolean separator) {
    final Border emptyBorder = BorderFactory.createEmptyBorder(0, 2, 0, 2);
    final Border separatorLeft = BorderFactory.createCompoundBorder(emptyBorder, new SeparatorBorder.Left());
    myPatches.add(panel);
    JComponent component = panel.getComponent();
    if (separator) {
      component.setBorder(separatorLeft);
    }
    else {
      component.setBorder(emptyBorder);
    }
    component.setOpaque(false);
  }
}
