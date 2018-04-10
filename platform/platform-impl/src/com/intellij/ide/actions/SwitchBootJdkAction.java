// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JdkBundle;
import com.intellij.util.JdkBundleList;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author denis
 */
public class SwitchBootJdkAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(SwitchBootJdkAction.class);

  private static final JavaVersion MIN_VERSION = JavaVersion.compose(8), MAX_VERSION = null;

  private static final String WINDOWS_X64_JVM_LOCATION = "Program Files/Java";
  private static final String WINDOWS_X86_JVM_LOCATION = "Program Files (x86)/Java";
  private static final String[] MAC_OS_JVM_LOCATIONS = {"/Library/Java/JavaVirtualMachines"};
  private static final String[] LINUX_JVM_LOCATIONS = {"/usr/lib/jvm", "/usr/java"};

  private static final String CONFIG_FILE_EXT =
    (!SystemInfo.isWindows ? ".jdk" : SystemInfo.is64Bit ? "64.exe.jdk" : ".exe.jdk");

  @Override
  public void actionPerformed(@Nullable AnActionEvent e) {
    Project project = e != null ? e.getProject() : null;
    new Task.Modal(project, "Looking for Available JDKs", true) {
      private JdkBundleList myBundleList;
      private File myConfigFile;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myBundleList = findJdkBundles(indicator);

        String selector = PathManager.getPathsSelector();
        File configDir = new File(selector != null ? PathManager.getDefaultConfigPathFor(selector) : PathManager.getConfigPath());
        String exeName = System.getProperty("idea.executable");
        if (exeName == null) exeName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
        myConfigFile = new File(configDir, exeName + CONFIG_FILE_EXT);
      }

      @Override
      public void onSuccess() {
        SwitchBootJdkDialog dialog = new SwitchBootJdkDialog(getProject(), myBundleList, myConfigFile.exists());
        if (!dialog.showAndGet()) return;

        try {
          storeSelection(myConfigFile, dialog.getSelectedFile());
        }
        catch (IOException ex) {
          LOG.error(ex);
          String title = "Failed to store boot JDK selection";
          String message = ex.getMessage();
          Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, message, NotificationType.ERROR), project);
          return;
        }

        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().restart(), ModalityState.NON_MODAL);
      }
    }.queue();
  }

  private static JdkBundleList findJdkBundles(@Nullable ProgressIndicator indicator) {
    JdkBundleList bundleList = new JdkBundleList();

    bundleList.addBundle(JdkBundle.createBoot());

    JdkBundle bundledJdk = JdkBundle.createBundled();
    if (bundledJdk != null && bundledJdk.isOperational()) {
      bundleList.addBundle(bundledJdk);
    }

    String[] locations = ArrayUtil.EMPTY_STRING_ARRAY;
    if (SystemInfo.isWindows) {
      String dir = SystemInfo.is32Bit ? WINDOWS_X86_JVM_LOCATION : WINDOWS_X64_JVM_LOCATION;
      locations = Stream.of(File.listRoots()).map(root -> new File(root, dir).getPath()).toArray(String[]::new);
    }
    else if (SystemInfo.isMac) {
      locations = MAC_OS_JVM_LOCATIONS;
    }
    else if (SystemInfo.isLinux) {
      locations = LINUX_JVM_LOCATIONS;
    }
    for (String location : locations) {
      if (indicator != null) indicator.checkCanceled();
      bundleList.addBundlesFromLocation(location, MIN_VERSION, MAX_VERSION);
    }

    return bundleList;
  }

  private static void storeSelection(File configFile, File selectedBundle) throws IOException {
    if (selectedBundle != null) {
      File configDir = configFile.getParentFile();
      if (!(configDir.isDirectory() || configDir.mkdirs())) {
        throw new IOException("Cannot create JDK config directory '" + configDir + "'");
      }
      try {
        FileUtil.writeToFile(configFile, selectedBundle.getPath());
      }
      catch (IOException e) {
        throw new IOException("Cannot write JDK config file '" + configFile + "': " + e.getMessage(), e);
      }
    }
    else if (!FileUtil.delete(configFile)) {
      throw new IOException("Cannot delete JDK config file '" + configFile + "'");
    }
  }

  private static class SwitchBootJdkDialog extends DialogWrapper implements DataProvider, CopyProvider {
    private static class JdkBundleItem {
      private final JdkBundle bundle;
      private JdkBundleItem(JdkBundle bundle) { this.bundle = bundle; }
    }

    private static final JdkBundleItem RESET = new JdkBundleItem(null);
    private static final JdkBundleItem CREATE = new JdkBundleItem(null);

    private static final Comparator<JdkBundle> BUNDLE_COMPARATOR = (b1, b2) -> {
      /* boot first, then by version in a descending order */
      if (b1.isBoot()) return -1;
      if (b2.isBoot()) return 1;
      int diff = b2.getBundleVersion().compareTo(b1.getBundleVersion());
      if (diff != 0) return diff;
      return b1.getLocation().getPath().compareTo(b2.getLocation().getPath());
    };

    private final JdkBundleList myPathsList;
    private final DefaultComboBoxModel<JdkBundleItem> myModel;
    private final ComboBox<JdkBundleItem> myComboBox;

    private SwitchBootJdkDialog(Project project, JdkBundleList pathsList, boolean customConfig) {
      super(project, true);

      myPathsList = pathsList;

      myModel = new DefaultComboBoxModel<>();
      if (customConfig) {
        myModel.addElement(RESET);
      }
      List<JdkBundle> bundles = new ArrayList<>(pathsList.getBundles());
      bundles.sort(BUNDLE_COMPARATOR);
      for (JdkBundle bundle : bundles) myModel.addElement(new JdkBundleItem(bundle));
      myModel.addElement(CREATE);

      myComboBox = new ComboBox<>(myModel);
      myComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
      myComboBox.setRenderer(new JdkBundleItemRenderer());
      myComboBox.addActionListener(e -> {
        if (myComboBox.getSelectedItem() == CREATE) chooseBundle();
        JdkBundleItem item = (JdkBundleItem)myComboBox.getSelectedItem();
        setOKActionEnabled(item == RESET || item != null && item.bundle != null && !item.bundle.isBoot());
      });

      setTitle("Switch IDE Boot JDK");
      setOKButtonText("Save and restart");
      init();
    }

    private void chooseBundle() {
      FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
        private JdkBundle selectedBundle;

        @Override
        public boolean isFileSelectable(VirtualFile file) {
          selectedBundle = null;
          if (super.isFileSelectable(file)) {
            JdkBundle bundle = JdkBundle.createBundle(new File(file.getPath()));
            if (bundle != null) {
              selectedBundle = bundle;
              return true;
            }
          }
          return false;
        }

        @Override
        public void validateSelectedFiles(VirtualFile[] files) throws Exception {
          super.validateSelectedFiles(files);
          assert files.length == 1 : Arrays.toString(files);
          if (selectedBundle == null) {
            throw new Exception("Not a valid JDK bundle.");
          }
          if (selectedBundle.getBundleVersion().compareTo(MIN_VERSION) < 0) {
            throw new Exception("JDK version mismatch (required: min " + MIN_VERSION + ", selected: " + selectedBundle.getBundleVersion() + ")");
          }
          Bitness arch = SystemInfo.is64Bit ? Bitness.x64 : Bitness.x32;
          if (selectedBundle.getBitness() != arch) {
            throw new Exception("JDK arch mismatch (required: " + arch + ", selected: " + selectedBundle.getBitness() + ")");
          }
        }
      };

      FileChooser.chooseFiles(descriptor, null, null, files -> {
        if (files.size() == 1) {
          File jdkFile = new File(files.get(0).getPath());
          JdkBundle existing = myPathsList.getBundle(jdkFile.getPath());
          if (existing != null) {
            ComboBoxModel<JdkBundleItem> model = myComboBox.getModel();
            for (int i = 0; i < model.getSize(); i++) {
              if (model.getElementAt(i).bundle == existing) {
                myComboBox.setSelectedIndex(i);
                break;
              }
            }
          }
          else {
            JdkBundle bundle = JdkBundle.createBundle(jdkFile);
            if (bundle != null) {
              myPathsList.addBundle(bundle);
              JdkBundleItem item = new JdkBundleItem(bundle);
              myModel.insertElementAt(item, myModel.getSize() - 1);
              myComboBox.setSelectedItem(item);
            }
            else {
              LOG.error("Cannot create bundle for path: " + jdkFile.getPath());
            }
          }
        }
      });
    }

    @Override
    protected JComponent createNorthPanel() {
      return new JBLabel("Select Boot JDK");
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myComboBox, BorderLayout.CENTER);
      DefaultActionGroup group = new DefaultActionGroup(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("SwitchBootJDKCopyAction", group, true);
      toolbar.setReservePlaceAutoPopupIcon(false);
      panel.add((Component)toolbar, BorderLayout.EAST);
      return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Nullable
    public File getSelectedFile() {
      JdkBundleItem item = (JdkBundleItem)myComboBox.getSelectedItem();
      return item != null && item.bundle != null ? item.bundle.getLocation() : null;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      File file = getSelectedFile();
      if (file != null) {
        CopyPasteManager.getInstance().setContents(new StringSelection(file.getAbsolutePath()));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return getSelectedFile() != null;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return getSelectedFile() != null;
    }

    @Override
    public Object getData(String dataId) {
      return PlatformDataKeys.COPY_PROVIDER.is(dataId) ? this : null;
    }

    private static class JdkBundleItemRenderer extends ListCellRendererWrapper<JdkBundleItem> {
      @Override
      public void customize(JList list, JdkBundleItem value, int index, boolean selected, boolean hasFocus) {
        if (value == RESET) {
          setText("<reset to default>");
        }
        else if (value == CREATE) {
          setText("...");
        }
        else if (value != null) {
          StringBuilder sb = new StringBuilder();
          sb.append('[').append(value.bundle.getBundleVersion());
          if (value.bundle.isBoot()) sb.append(" boot");
          if (value.bundle.isBundled()) sb.append(" bundled");
          sb.append("] ").append(value.bundle.getLocation());
          setText(sb.toString());
          if (value.bundle.isBoot()) {
            setForeground(JBColor.GREEN);
          }
        }
      }
    }
  }
}