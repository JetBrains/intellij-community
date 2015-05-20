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
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author denis
 */
public class SwitchBootJdkAction extends AnAction implements DumbAware {

  @NonNls private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.SwitchBootJdkAction");
  @NonNls private static final String productJdkConfigFileName = ApplicationNamesInfo.getInstance().getScriptName() + ".jdk";
  @NonNls private static final File productJdkConfigFile = new File(PathManager.getConfigPath(), productJdkConfigFileName);
  @NonNls private static final File customJdkFile = new File(PathManager.getHomePath() + File.separator + "jre" + File.separator + "jdk");

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!SystemInfo.isMac || !customJdkFile.exists()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setText("Switch Boot JDK");
  }

  public static List<JdkBundleDescriptor> getBundlesFromFile(@NotNull File fileWithBundles) {
    InputStream stream = null;
    InputStreamReader inputStream;
    BufferedReader bufferedReader;

    List<JdkBundleDescriptor> list = new ArrayList<JdkBundleDescriptor>();


    try {
      stream = new FileInputStream(fileWithBundles);
      inputStream = new InputStreamReader(stream, Charset.forName("UTF-8"));
      bufferedReader = new BufferedReader(inputStream);

      String line;

      while ((line = bufferedReader.readLine()) != null) {
        File file = new File(line);
        if (file.exists()) {
          list.add(new JdkBundleDescriptor(file, file.getName()));
        }
      }

    } catch (IllegalStateException e) {
      // The device builders can throw IllegalStateExceptions if
      // build gets called before everything is properly setup
      LOG.error(e);
    } catch (Exception e) {
      LOG.error("Error reading JDK bundles", e);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignore) {}
      }
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
        fooWriter = new FileWriter(productJdkConfigFile, false);
        fooWriter.write(selectedJdkBundleFile.getAbsolutePath());
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

  private static class JdkBundleDescriptor {
    private File bundleAsFile;
    private String visualRepresentation;

    public JdkBundleDescriptor(File bundleAsFile, String visualRepresentation) {
      this.bundleAsFile = bundleAsFile;
      this.visualRepresentation = visualRepresentation;
    }

    public File getBundleAsFile() {
      return bundleAsFile;
    }

    public String getVisualRepresentation() {
      return visualRepresentation;
    }
  }

  private static class SwitchBootJdkDialog extends DialogWrapper {

    @NotNull private final ComboBox myComboBox;

    protected SwitchBootJdkDialog(@Nullable Project project, List<JdkBundleDescriptor> jdkBundlesList) {
      super(project, false);

      final ArrayList<JdkBundleDescriptor> pathsList = JdkUtil.findJdkPaths();
      if (!jdkBundlesList.isEmpty()) {
        pathsList.add(0, jdkBundlesList.get(0));
      }

      myComboBox = new ComboBox();

      DefaultComboBoxModel model = new DefaultComboBoxModel();

      for (JdkBundleDescriptor jdkBundlePath : pathsList) {
        if (!jdkBundlesList.isEmpty() && FileUtil.filesEqual(jdkBundlePath.getBundleAsFile(),jdkBundlesList.get(0).getBundleAsFile())) {
          continue;
        }
        model.addElement(jdkBundlePath);
      }

      myComboBox.setModel(model);
      myComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          JdkBundleDescriptor jdkBundleDescriptor = ((JdkBundleDescriptor)value);
          setText(jdkBundleDescriptor.getVisualRepresentation());
        }
      });

      setTitle("Switch IDE Boot JDK");
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
      return ((JdkBundleDescriptor)myComboBox.getSelectedItem()).bundleAsFile;
    }
  }

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String STANDARD_JDK_6_LOCATION_ON_MAC_OS_X = "/System/Library/Java/JavaVirtualMachines/";

  private static class JdkUtil {
    private static ArrayList <JdkBundleDescriptor> findJdkPaths () {
      ArrayList<JdkBundleDescriptor> jdkPathsList = new ArrayList<JdkBundleDescriptor>();
      if (!SystemInfo.isMac) return jdkPathsList;


      if (customJdkFile.exists()) {
          jdkPathsList.add(new JdkBundleDescriptor(customJdkFile, "JDK bundled with IDE"));
      }

      jdkPathsList.addAll(jdkBundlesFromLocation(STANDARD_JDK_6_LOCATION_ON_MAC_OS_X, "1.6.0"));
      jdkPathsList.addAll(jdkBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, "jdk1.8.0_(\\d*).jdk"));

      return jdkPathsList;
    }

    private static ArrayList<JdkBundleDescriptor> jdkBundlesFromLocation(String jdkLocationOnMacOsX, String filter) {

      ArrayList<JdkBundleDescriptor> localJdkPathsList = new ArrayList<JdkBundleDescriptor>();

      File standardJdkLocationOnMacFile = new File(jdkLocationOnMacOsX);

      if (!standardJdkLocationOnMacFile.exists()) {
        LOG.info("Location does not exists: " + jdkLocationOnMacOsX);
        return localJdkPathsList;
      }

      File[] filesInStandardJdkLocation = standardJdkLocationOnMacFile.listFiles();

      int latestUpdateNumber = 0;
      JdkBundleDescriptor latestBundle = null;

      String regex = filter;

      Pattern p = Pattern.compile(regex);

      for (File possibleJdkBundle : filesInStandardJdkLocation) {
        // todo add some logic to verify the bundle

        Matcher m = p.matcher(possibleJdkBundle.getName());


        while(m.find()) {
          try {
            if (m.groupCount() > 0) {
              int updateNumber = Integer.parseInt(m.group(1));
              if (latestUpdateNumber < updateNumber) {
                latestBundle = new JdkBundleDescriptor(possibleJdkBundle, possibleJdkBundle.getName());
              }
            } else {
              latestBundle = new JdkBundleDescriptor(possibleJdkBundle, possibleJdkBundle.getName());
            }
          } catch (NumberFormatException nfe) {
            LOG.error("Fail parsing update number");
          }
        }

      }

      if (latestBundle != null) {
        localJdkPathsList.add(latestBundle);
      }

      return localJdkPathsList;
    }
  }

}
