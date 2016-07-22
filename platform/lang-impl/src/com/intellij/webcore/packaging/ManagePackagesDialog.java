/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.webcore.packaging;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * User: catherine
 * <p/>
 * UI for installing python packages
 */
public class ManagePackagesDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(ManagePackagesDialog.class);

  @NotNull private final Project myProject;
  private final PackageManagementService myController;

  private JPanel myFilter;
  private JPanel myMainPanel;
  private JEditorPane myDescriptionTextArea;
  private JBList myPackages;
  private JButton myInstallButton;
  private JCheckBox myOptionsCheckBox;
  private JTextField myOptionsField;
  private JCheckBox myInstallToUser;
  private JComboBox myVersionComboBox;
  private JCheckBox myVersionCheckBox;
  private JButton myManageButton;
  private final PackagesNotificationPanel myNotificationArea;
  private JSplitPane mySplitPane;
  private JPanel myNotificationsAreaPlaceholder;
  private PackagesModel myPackagesModel;
  private String mySelectedPackageName;
  private final Set<String> myInstalledPackages;
  @Nullable private final PackageManagementService.Listener myPackageListener;

  private Set<String> myCurrentlyInstalling = new HashSet<>();
  protected final ListSpeedSearch myListSpeedSearch;

  public ManagePackagesDialog(@NotNull Project project, final PackageManagementService packageManagementService,
                              @Nullable final PackageManagementService.Listener packageListener) {
    super(project, true);
    myProject = project;
    myController = packageManagementService;

    myPackageListener = packageListener;
    init();
    setTitle("Available Packages");
    myPackages = new JBList();
    myNotificationArea = new PackagesNotificationPanel();
    myNotificationsAreaPlaceholder.add(myNotificationArea.getComponent(), BorderLayout.CENTER);

    final AnActionButton reloadButton = new AnActionButton("Reload List of Packages", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPackages.setPaintBusy(true);
        final Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(() -> {
          try {
            myController.reloadAllPackages();
            initModel();
            myPackages.setPaintBusy(false);
          }
          catch (final IOException e1) {
            application.invokeLater(() -> {
              //noinspection DialogTitleCapitalization
              Messages.showErrorDialog(myMainPanel, "Error updating package list: " + e1.getMessage(), "Reload List of Packages");
              myPackages.setPaintBusy(false);
            }, ModalityState.any());
          }
        });
      }
    };
    myListSpeedSearch = new ListSpeedSearch(myPackages, new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        if (o instanceof RepoPackage)
          return ((RepoPackage)o).getName();
        return "";
      }
    });
    JPanel packagesPanel = ToolbarDecorator.createDecorator(myPackages)
      .disableAddAction()
      .disableUpDownActions()
      .disableRemoveAction()
      .addExtraAction(reloadButton)
      .createPanel();
    packagesPanel.setPreferredSize(new Dimension(JBUI.scale(400), -1));
    packagesPanel.setMinimumSize(new Dimension(JBUI.scale(100), -1));
    myPackages.setFixedCellWidth(0);
    myPackages.setFixedCellHeight(JBUI.scale(22));
    myPackages.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySplitPane.setLeftComponent(packagesPanel);

    myPackages.addListSelectionListener(new MyPackageSelectionListener());
    myInstallToUser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myController.installToUserChanged(myInstallToUser.isSelected());
      }
    });
    myOptionsCheckBox.setEnabled(false);
    myVersionCheckBox.setEnabled(false);
    myVersionCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myVersionComboBox.setEnabled(myVersionCheckBox.isSelected());
      }
    });

    UiNotifyConnector.doWhenFirstShown(myPackages, () -> initModel());
    myOptionsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myOptionsField.setEnabled(myOptionsCheckBox.isSelected());
      }
    });
    myInstallButton.setEnabled(false);
    myDescriptionTextArea.addHyperlinkListener(new PluginManagerMain.MyHyperlinkListener());
    addInstallAction();
    myInstalledPackages = new HashSet<>();
    updateInstalledPackages();
    addManageAction();
    myPackages.setCellRenderer(new MyTableRenderer());

    if (myController.canInstallToUser()) {
      myInstallToUser.setVisible(true);
      myInstallToUser.setSelected(myController.isInstallToUserSelected());
      myInstallToUser.setText(myController.getInstallToUserText());
    }
    else {
      myInstallToUser.setVisible(false);
    }
    myMainPanel.setPreferredSize(new Dimension(JBUI.scale(900), JBUI.scale(700)));
  }

  public void selectPackage(@NotNull InstalledPackage pkg) {
    mySelectedPackageName = pkg.getName();
    doSelectPackage(mySelectedPackageName);
  }

  private void addManageAction() {
    if (myController.getAllRepositories() != null) {
      myManageButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          ManageRepoDialog dialog = new ManageRepoDialog(myProject, myController);
          dialog.show();
        }
      });
    }
    else {
      myManageButton.setVisible(false);
    }
  }

  private void addInstallAction() {
    myInstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        final Object pyPackage = myPackages.getSelectedValue();
        if (pyPackage instanceof RepoPackage) {
          RepoPackage repoPackage = (RepoPackage)pyPackage;

          String extraOptions = null;
          if (myOptionsCheckBox.isEnabled() && myOptionsCheckBox.isSelected()) {
            extraOptions = myOptionsField.getText();
          }

          String version = null;
          if (myVersionCheckBox.isEnabled() && myVersionCheckBox.isSelected()) {
            version = (String) myVersionComboBox.getSelectedItem();
          }

          final PackageManagementService.Listener listener = new PackageManagementService.Listener() {
            @Override
            public void operationStarted(final String packageName) {
              if (!ApplicationManager.getApplication().isDispatchThread()) {
                ApplicationManager.getApplication().invokeLater(() -> handleInstallationStarted(packageName), ModalityState.stateForComponent(myMainPanel));
              }
              else {
                handleInstallationStarted(packageName);
              }
            }

            @Override
            public void operationFinished(final String packageName,
                                          @Nullable final PackageManagementService.ErrorDescription errorDescription) {
              if (!ApplicationManager.getApplication().isDispatchThread()) {
                ApplicationManager.getApplication().invokeLater(() -> handleInstallationFinished(packageName, errorDescription), ModalityState.stateForComponent(myMainPanel));
              }
              else {
                handleInstallationFinished(packageName, errorDescription);
              }
            }
          };
          myController.installPackage(repoPackage, version, false, extraOptions, listener, myInstallToUser.isSelected());
          myInstallButton.setEnabled(false);
        }
      }
    });
  }

  private void handleInstallationStarted(String packageName) {
    setDownloadStatus(true);
    myCurrentlyInstalling.add(packageName);
    if (myPackageListener != null) {
      myPackageListener.operationStarted(packageName);
    }
    myPackages.repaint();
  }

  private void handleInstallationFinished(String packageName, PackageManagementService.ErrorDescription errorDescription) {
    if (myPackageListener != null) {
      myPackageListener.operationFinished(packageName, errorDescription);
    }
    setDownloadStatus(false);
    myNotificationArea.showResult(packageName, errorDescription);

    updateInstalledPackages();

    myCurrentlyInstalling.remove(packageName);
    myPackages.repaint();
  }

  private void updateInstalledPackages() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final Collection<InstalledPackage> installedPackages = myController.getInstalledPackages();
        UIUtil.invokeLaterIfNeeded(() -> {
          myInstalledPackages.clear();
          for (InstalledPackage pkg : installedPackages) {
            myInstalledPackages.add(pkg.getName());
          }
        });
      }
      catch(IOException e) {
        LOG.info("Error updating list of installed packages:" + e);
      }
    });
  }

  public void initModel() {
    setDownloadStatus(true);
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      try {
        myPackagesModel = new PackagesModel(myController.getAllPackages());

        application.invokeLater(() -> {
          myPackages.setModel(myPackagesModel);
          ((MyPackageFilter)myFilter).filter();
          doSelectPackage(mySelectedPackageName);
          setDownloadStatus(false);
        }, ModalityState.any());
      }
      catch (final IOException e) {
        application.invokeLater(() -> {
          if (myMainPanel.isShowing()) {
            Messages.showErrorDialog(myMainPanel, "Error loading package list:" + e.getMessage(), "Packages");
          }
          setDownloadStatus(false);
        }, ModalityState.any());
      }
    });
  }

  private void doSelectPackage(@Nullable String packageName) {
    PackagesModel packagesModel = ObjectUtils.tryCast(myPackages.getModel(), PackagesModel.class);
    if (packageName == null || packagesModel == null) {
      return;
    }
    for (int i = 0; i < packagesModel.getSize(); i++) {
      RepoPackage repoPackage = packagesModel.getElementAt(i);
      if (packageName.equals(repoPackage.getName())) {
        myPackages.setSelectedIndex(i);
        myPackages.ensureIndexIsVisible(i);
        break;
      }
    }
  }

  protected void setDownloadStatus(boolean status) {
    myPackages.setPaintBusy(status);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myFilter = new MyPackageFilter();
  }

  public void setOptionsText(@NotNull String optionsText) {
    myOptionsField.setText(optionsText);
  }

  private class MyPackageFilter extends FilterComponent {
    public MyPackageFilter() {
      super("PACKAGE_FILTER", 5);
      getTextEditor().addKeyListener(new KeyAdapter() {
        public void keyPressed(final KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            e.consume();
            filter();
            myPackages.requestFocus();
          } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            onEscape(e);
          }
        }
      });
    }

    public void filter() {
      if (myPackagesModel != null)
        myPackagesModel.filter(getFilter());
    }
  }

  private class PackagesModel extends CollectionListModel<RepoPackage> {
    protected final List<RepoPackage> myFilteredOut = new ArrayList<>();
    protected List<RepoPackage> myView = new ArrayList<>();

    public PackagesModel(List<RepoPackage> packages) {
      super(packages);
      myView = packages;
    }

    public void add(String urlResource, String element) {
      super.add(new RepoPackage(element, urlResource));
    }

    protected void filter(final String filter) {
      final Collection<RepoPackage> toProcess = toProcess();

      toProcess.addAll(myFilteredOut);
      myFilteredOut.clear();

      final ArrayList<RepoPackage> filtered = new ArrayList<>();

      RepoPackage toSelect = null;
      for (RepoPackage repoPackage : toProcess) {
        final String packageName = repoPackage.getName();
        if (StringUtil.containsIgnoreCase(packageName, filter)) {
          filtered.add(repoPackage);
        }
        else {
          myFilteredOut.add(repoPackage);
        }
        if (StringUtil.equalsIgnoreCase(packageName, filter)) toSelect = repoPackage;
      }
      filter(filtered, toSelect);
    }

    public void filter(List<RepoPackage> filtered, @Nullable final RepoPackage toSelect){
      myView.clear();
      myPackages.clearSelection();
      for (RepoPackage repoPackage : filtered) {
        myView.add(repoPackage);
      }
      if (toSelect != null)
        myPackages.setSelectedValue(toSelect, true);
      Collections.sort(myView);
      fireContentsChanged(this, 0, myView.size());
    }

    @Override
    public RepoPackage getElementAt(int index) {
      return myView.get(index);
    }

    protected ArrayList<RepoPackage> toProcess() {
      return new ArrayList<>(myView);
    }

    @Override
    public int getSize() {
      return myView.size();
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myFilter;
  }

  private class MyPackageSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent event) {
      myOptionsCheckBox.setEnabled(myPackages.getSelectedIndex() >= 0);
      myVersionCheckBox.setEnabled(myPackages.getSelectedIndex() >= 0);
      myOptionsCheckBox.setSelected(false);
      myVersionCheckBox.setSelected(false);
      myVersionComboBox.setEnabled(false);
      myOptionsField.setEnabled(false);
      myDescriptionTextArea.setText("<html><body style='text-align: center;padding-top:20px;'>Loading...</body></html>");
      final Object pyPackage = myPackages.getSelectedValue();
      if (pyPackage instanceof RepoPackage) {
        final String packageName = ((RepoPackage)pyPackage).getName();
        mySelectedPackageName = packageName;
        myVersionComboBox.removeAllItems();
        if (myVersionCheckBox.isEnabled()) {
          myController.fetchPackageVersions(packageName, new CatchingConsumer<List<String>, Exception>() {
            @Override
            public void consume(final List<String> releases) {
              ApplicationManager.getApplication().invokeLater(() -> {
                if (myPackages.getSelectedValue() == pyPackage) {
                  myVersionComboBox.removeAllItems();
                  for (String release : releases) {
                    myVersionComboBox.addItem(release);
                  }
                }
              }, ModalityState.any());
            }

            @Override
            public void consume(Exception e) {
              LOG.info("Error retrieving releases", e);
            }
          });
        }
        myInstallButton.setEnabled(!myCurrentlyInstalling.contains(packageName));

        myController.fetchPackageDetails(packageName, new CatchingConsumer<String, Exception>() {
          @Override
          public void consume(final String details) {
            UIUtil.invokeLaterIfNeeded(() -> {
              if (myPackages.getSelectedValue() == pyPackage) {
                myDescriptionTextArea.setText(details);
                myDescriptionTextArea.setCaretPosition(0);
              }/* else {
                 do nothing, because other package gets selected
              }*/
            });
          }

          @Override
          public void consume(Exception exception) {
            UIUtil.invokeLaterIfNeeded(() -> myDescriptionTextArea.setText("No information available"));
            LOG.info("Error retrieving package details", exception);
          }
        });
      }
      else {
        myInstallButton.setEnabled(false);
        myDescriptionTextArea.setText("");
      }
    }
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[0];
  }

  private class MyTableRenderer extends DefaultListCellRenderer {
    private JLabel myNameLabel = new JLabel();
    private JLabel myRepositoryLabel = new JLabel();
    private JPanel myPanel = new JPanel(new BorderLayout());

    private MyTableRenderer() {
      myPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 1));
      // setting border.left on myPanel doesn't prevent from myRepository being painted on left empty area
      myNameLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

      myRepositoryLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(myRepositoryLabel, BorderLayout.EAST);
      myNameLabel.setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      if (value instanceof RepoPackage) {
        RepoPackage repoPackage = (RepoPackage) value;
        String name = repoPackage.getName();
        if (myCurrentlyInstalling.contains(name)) {
          final String colorCode = UIUtil.isUnderDarcula() ? "589df6" : "0000FF";
          name = "<html><body>" + repoPackage.getName() + " <font color=\"#" + colorCode + "\">(installing)</font></body></html>";
        }
        myNameLabel.setText(name);
        myRepositoryLabel.setText(repoPackage.getRepoUrl());
        Component orig = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Color fg = orig.getForeground();
        myNameLabel.setForeground(myInstalledPackages.contains(name) ? PlatformColors.BLUE : fg);
      }
      myRepositoryLabel.setForeground(JBColor.GRAY);

      final Color bg;
      if (isSelected) {
        bg = UIUtil.getListSelectionBackground();
      }
      else {
        bg = index % 2 == 1 ? UIUtil.getListBackground() : UIUtil.getDecoratedRowColor();
      }
      myPanel.setBackground(bg);
      myNameLabel.setBackground(bg);
      myRepositoryLabel.setBackground(bg);
      return myPanel;
    }
  }
}
