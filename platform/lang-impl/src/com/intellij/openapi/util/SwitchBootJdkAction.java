/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.JdkBundle;
import com.intellij.util.JdkBundleList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * @author denis
 */
public class SwitchBootJdkAction extends AnAction implements DumbAware {
  @NotNull private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.SwitchBootJdkAction");
  @NotNull private static final String productJdkConfigFileName =
    getExecutable() + (SystemInfo.isWindows ? ((SystemInfo.is64Bit) ? "64.exe.jdk" : ".exe.jdk") : ".jdk");

  @Nullable private static final String pathsSelector = PathManager.getPathsSelector();
  @NotNull private static final File productJdkConfigDir = new File(pathsSelector != null ?
                                                                    PathManager.getDefaultConfigPathFor(pathsSelector) :
                                                                    PathManager.getConfigPath());

  @NotNull private static final File productJdkConfigFile = new File(productJdkConfigDir, productJdkConfigFileName);

  @NotNull private static final File bundledJdkFile = getBundledJDKFile();



  @NotNull
  private static File getBundledJDKFile() {
    return new File(SystemInfo.isMac ? "jdk" : "jre" + (JdkBundle.runtimeBitness == Bitness.x64 ? "64" : ""));
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("Switch Boot JDK");
  }

  @Override
  public void actionPerformed(AnActionEvent event) {

    if (!productJdkConfigDir.exists()) {
      try {
        if (!productJdkConfigDir.mkdirs()) {
          LOG.error("Could not create " + productJdkConfigDir + " productJdkConfigDir");
          return;
        }
        if (!productJdkConfigFile.exists()) {
          if (!productJdkConfigFile.createNewFile()) {
            LOG.error("Could not create " + productJdkConfigFileName + " productJdkConfigFile");
            return;
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
    }

    SwitchBootJdkDialog dialog = new SwitchBootJdkDialog();
    if (dialog.showAndGet()) {
      File selectedJdkBundleFile = dialog.getSelectedFile();
      if (selectedJdkBundleFile == null) {
        LOG.error("SwitchBootJdkDialog returns null selection");
        return;
      }

      FileWriter fooWriter = null;
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        fooWriter = new FileWriter(productJdkConfigFile, false);
        fooWriter.write(selectedJdkBundleFile.getPath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          if (fooWriter != null) {
            fooWriter.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      ApplicationManager.getApplication().restart();
    }
  }

  private static class SwitchBootJdkDialog extends DialogWrapper implements DataProvider, CopyProvider {

    static class JdkBundleItem {
      @Nullable private JdkBundle myBundle;

      public JdkBundleItem(@Nullable JdkBundle bundle) {
        myBundle = bundle;
      }

      @Nullable public JdkBundle getBundle() {
        return myBundle;
      }
    }

    @NotNull private final ComboBox myComboBox;

    private SwitchBootJdkDialog() {
      super((Project)null, false);

      final JdkBundleList pathsList = findJdkPaths();

      myComboBox = new ComboBox();

      final DefaultComboBoxModel<JdkBundleItem> model = new DefaultComboBoxModel<>();

      for (JdkBundle jdkBundlePath : pathsList.toArrayList()) {
        //noinspection unchecked
        model.addElement(new JdkBundleItem(jdkBundlePath));
      }

      model.addElement(new JdkBundleItem(null));

      //noinspection unchecked
      myComboBox.setModel(model);

      //noinspection unchecked
      myComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          JdkBundle jdkBundleDescriptor = ((JdkBundleItem)value).getBundle();
          if (jdkBundleDescriptor != null) {
            if (jdkBundleDescriptor.isBoot()) {
              setForeground(JBColor.DARK_GRAY);
            }
            setText(jdkBundleDescriptor.getVisualRepresentation());
          }
          else {
            setText("...");
          }
        }
      });

      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myComboBox.getSelectedItem() == null) {
            LOG.error("Unexpected nullable selection");
            return;
          }

          JdkBundleItem item = (JdkBundleItem)myComboBox.getSelectedItem();

          if (item.getBundle() == null) {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
              JdkBundle selectedBundle;

              @Override
              public boolean isFileSelectable(final VirtualFile file) {
                selectedBundle = null;
                if (!super.isFileSelectable(file)) return false;
                // allow selection of JDK of any arch, so that to warn about possible arch mismatch during validation
                JdkBundle bundle = JdkBundle.createBundle(new File(file.getPath()), false, false, false);
                if (bundle == null) return false;
                Version version = bundle.getVersion();
                selectedBundle = bundle;
                return version != null && !version.lessThan(JDK8_VERSION.major, JDK8_VERSION.minor, JDK8_VERSION.bugfix);
              }

              @Override
              public void validateSelectedFiles(VirtualFile[] files) throws Exception {
                super.validateSelectedFiles(files);
                assert files.length == 1;
                if (selectedBundle == null) {
                  throw new Exception("Invalid JDK bundle!");
                }
                if (selectedBundle.getBitness() != JdkBundle.runtimeBitness) {
                  //noinspection SpellCheckingInspection
                  throw new Exception("JDK arch mismatch! Your IDE's arch is " + JdkBundle.runtimeBitness);
                }
              }
            };

            FileChooser.chooseFiles(descriptor, null, null, files -> {
              if (files.size() > 0) {
                final File jdkFile = new File(files.get(0).getPath());
                JdkBundle selectedJdk = pathsList.getBundle(jdkFile.getPath());
                JdkBundleItem jdkBundleItem;
                if (selectedJdk == null) {
                  selectedJdk = JdkBundle.createBundle(jdkFile, false, false);
                  if (selectedJdk != null) {
                    pathsList.addBundle(selectedJdk, true);
                    if (model.getSize() > 0) {
                      jdkBundleItem = new JdkBundleItem(selectedJdk);
                      model.insertElementAt(jdkBundleItem, model.getSize() - 1);
                    }
                    else {
                      jdkBundleItem = new JdkBundleItem(selectedJdk);
                      model.addElement(jdkBundleItem);
                    }
                  }
                  else {
                    LOG.error("Cannot create bundle for path: " + jdkFile.getPath());
                    return;
                  }
                } else {
                  jdkBundleItem = new JdkBundleItem(selectedJdk);
                }
                myComboBox.setSelectedItem(jdkBundleItem);
              }
            });
          }

          item = (JdkBundleItem)myComboBox.getSelectedItem();

          if (item == null || item.getBundle() == null) {
            item = model.getElementAt(0);
            myComboBox.setSelectedItem(item);
          }

          setOKActionEnabled(item.getBundle() != null && !item.getBundle().isBoot());
        }
      });
      myComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

      setTitle("Switch IDE Boot JDK");
      setOKActionEnabled(false); // First item is a boot jdk
      init();
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      return new JBLabel("Select Boot JDK");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myComboBox, BorderLayout.CENTER);
      ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
        .createActionToolbar("SwitchBootJDKCopyAction", new DefaultActionGroup(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY)), true);
      toolbar.setReservePlaceAutoPopupIcon(false);
      panel.add(toolbar , BorderLayout.EAST);
      return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Nullable
    public File getSelectedFile() {
      final JdkBundleItem item = (JdkBundleItem)myComboBox.getSelectedItem();
      if (item == null) return null;

      final JdkBundle bundle = item.getBundle();

      return bundle != null ? bundle.getLocation() : null;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      File file = getSelectedFile();
      if (file == null) return;
      CopyPasteManager.getInstance().setContents(new StringSelection(file.getAbsolutePath()));
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return getSelectedFile() != null;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return getSelectedFile() != null;
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) return this;
      return null;
    }
  }

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String [] STANDARD_JVM_LOCATIONS_ON_LINUX = new String[] {
    "/usr/lib/jvm/", // Ubuntu
    "/usr/java/"     // Fedora
  };
  private static final String STANDARD_JVM_X64_LOCATIONS_ON_WINDOWS = "Program Files/Java";

  private static final String STANDARD_JVM_X86_LOCATIONS_ON_WINDOWS = "Program Files (x86)/Java";

  private static final Version JDK8_VERSION = new Version(1, 8, 0);

  @NotNull
  public static JdkBundleList findJdkPaths() {
    JdkBundle bootJdk = JdkBundle.createBoot();

    JdkBundleList jdkBundleList = new JdkBundleList();
    if (bootJdk != null) {
      jdkBundleList.addBundle(bootJdk, true);
    }

    if (new File(PathManager.getHomePath() + File.separator + bundledJdkFile).exists()) {
      JdkBundle bundledJdk = JdkBundle.createBundle(bundledJdkFile, false, true);
      if (bundledJdk != null) {
        jdkBundleList.addBundle(bundledJdk, true);
      }
    }

    if (SystemInfo.isMac) {
      jdkBundleList.addBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK8_VERSION, null);
    }
    else if (SystemInfo.isLinux) {
      for (String location : STANDARD_JVM_LOCATIONS_ON_LINUX) {
        jdkBundleList.addBundlesFromLocation(location, JDK8_VERSION, null);
      }
    }
    else if (SystemInfo.isWindows) {
      for (File root : File.listRoots()) {
        if (SystemInfo.is32Bit) {
          jdkBundleList.addBundlesFromLocation(new File(root, STANDARD_JVM_X86_LOCATIONS_ON_WINDOWS).getAbsolutePath(), JDK8_VERSION, null);
        }
        else {
          jdkBundleList.addBundlesFromLocation(new File(root, STANDARD_JVM_X64_LOCATIONS_ON_WINDOWS).getAbsolutePath(), JDK8_VERSION, null);
        }
      }
    }

    return jdkBundleList;
  }

  @NotNull
  private static String getExecutable() {
    final String executable = System.getProperty("idea.executable");
    return executable != null ? executable : ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
  }
}
