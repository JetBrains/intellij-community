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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.JdkBundle;
import com.intellij.util.JdkBundleList;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author denis
 */
public class SwitchBootJdkAction extends AnAction implements DumbAware {
  @NonNls private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.SwitchBootJdkAction");
  @NonNls private static final String productJdkConfigFileName = getExecutable() + ".jdk";
  @NonNls private static final File productJdkConfigFile = new File(PathManager.getConfigPath(), productJdkConfigFileName);
  @NonNls private static final File bundledJdkFile = getBundledJDKFile();



  @NotNull
  private static File getBundledJDKFile() {
    StringBuilder bundledJDKPath = new StringBuilder("jre");
    if (SystemInfo.isMac) {
      bundledJDKPath.append(File.separator).append("jdk");
    }
    return new File(bundledJDKPath.toString());
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!(SystemInfo.isMac || SystemInfo.isLinux)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setText("Switch Boot JDK");
  }

  private static List<JdkBundle> getBundlesFromFile(@NotNull File fileWithBundles) {
    List<JdkBundle> list = new ArrayList<JdkBundle>();
    try {
      for (String line : FileUtil.loadLines(fileWithBundles, "UTF-8")) {
        File storedFile = new File(line);
        final boolean isBundled = !storedFile.isAbsolute();
        File actualFile = isBundled ? new File(PathManager.getHomePath(), storedFile.getPath()) : storedFile;
        if (actualFile.exists()) {
          list.add(JdkBundle.createBundle(storedFile, false, isBundled));
        }
      }
    } catch (IllegalStateException e) {
      // The device builders can throw IllegalStateExceptions if
      // build gets called before everything is properly setup
      LOG.error(e);
    } catch (IOException e) {
      LOG.error("Error reading JDK bundles", e);
    }
    return list;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {

    if (!productJdkConfigFile.exists()) {
      try {
        if (!productJdkConfigFile.createNewFile()){
          LOG.error("Could not create " + productJdkConfigFileName + " productJdkConfigFile");
          return;
        }
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
    }

    SwitchBootJdkDialog dialog = new SwitchBootJdkDialog(null, getBundlesFromFile(productJdkConfigFile));
    if (dialog.showAndGet()) {
      File selectedJdkBundleFile = dialog.getSelectedFile();
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

  private static class SwitchBootJdkDialog extends DialogWrapper {

    @NotNull private final ComboBox myComboBox;

    private SwitchBootJdkDialog(@Nullable Project project, final List<JdkBundle> jdkBundlesList) {
      super(project, false);

      final JdkBundleList pathsList = findJdkPaths();

      myComboBox = new ComboBox();

      DefaultComboBoxModel model = new DefaultComboBoxModel();

      for (JdkBundle jdkBundlePath : pathsList.toArrayList()) {
        //noinspection unchecked
        model.addElement(jdkBundlePath);
      }

      model.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) { }

        @Override
        public void intervalRemoved(ListDataEvent e) { }

        @Override
        public void contentsChanged(ListDataEvent e) {
          setOKActionEnabled(!((JdkBundle)myComboBox.getSelectedItem()).isBoot());
        }
      });

      //noinspection unchecked
      myComboBox.setModel(model);

      myComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value != null) {
            JdkBundle jdkBundleDescriptor = ((JdkBundle)value);
            if (jdkBundleDescriptor.isBoot()) {
              setForeground(JBColor.DARK_GRAY);
            }
            setText(jdkBundleDescriptor.getVisualRepresentation());
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Null value has been passed to a cell renderer. Available JDKs count: " + pathsList.toArrayList().size());
              StringBuilder jdkNames = new StringBuilder();
              for (JdkBundle jdkBundlePath : pathsList.toArrayList()) {
                if (!jdkBundlesList.isEmpty()) {
                  continue;
                }
                jdkNames.append(jdkBundlePath.getVisualRepresentation()).append("; ");
              }
              if (LOG.isDebugEnabled()) {
                LOG.debug("Available JDKs names: " + jdkNames.toString());
              }
            }
          }
        }
      });

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
      return myComboBox;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    public File getSelectedFile() {
      return ((JdkBundle)myComboBox.getSelectedItem()).getLocation();
    }
  }

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String [] STANDARD_JVM_LOCATIONS_ON_LINUX = new String[] {
    "/usr/lib/jvm/", // Ubuntu
    "/usr/java/"     // Fedora
  };

  private static final Version JDK8_VERSION = new Version(1, 8, 0);

  @NotNull
  private static JdkBundleList findJdkPaths() {
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

    return jdkBundleList;
  }

  @NotNull
  private static String getExecutable() {
    final String executable = System.getProperty("idea.executable");
    return executable != null ? executable : ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
  }
}
