package com.intellij.webcore.packaging;

import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class InstalledPackagesPanel extends JPanel {
  protected final JButton myInstallButton;
  private final JButton myUninstallButton;
  private final JButton myUpgradeButton;

  protected final JBTable myPackagesTable;
  private DefaultTableModel myPackagesTableModel;
  protected PackageManagementService myPackageManagementService;
  protected final Project myProject;
  protected final PackagesNotificationPanel myNotificationArea;
  protected final List<Consumer<Sdk>> myPathChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Set<String> myCurrentlyInstalling = ContainerUtil.newHashSet();
  private final Set<InstalledPackage> myWaitingToUpgrade = ContainerUtil.newHashSet();

  public InstalledPackagesPanel(Project project, PackagesNotificationPanel area) {
    super(new GridBagLayout());
    myProject = project;
    myNotificationArea = area;
    myInstallButton = new JButton("Install");
    myUninstallButton = new JButton("Uninstall");
    myUpgradeButton = new JButton("Upgrade");
    myInstallButton.setMnemonic('I');
    myUninstallButton.setMnemonic('U');
    myUpgradeButton.setMnemonic('p');

    myInstallButton.setEnabled(false);
    myUninstallButton.setEnabled(false);
    myUpgradeButton.setEnabled(false);

    myPackagesTableModel = new DefaultTableModel(new String[]{"Package", "Version", "Latest"}, 0) {
      @Override
      public boolean isCellEditable(int i, int i1) {
        return false;
      }
    };
    final TableCellRenderer tableCellRenderer = new MyTableCellRenderer();
    myPackagesTable = new JBTable(myPackagesTableModel) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return tableCellRenderer;
      }
    };
    myPackagesTable.getTableHeader().setReorderingAllowed(false);

    Insets anInsets = new Insets(2, 2, 2, 2);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPackagesTable,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setPreferredSize(new Dimension(500, 500));
    add(scrollPane, new GridBagConstraints(0, 0, 1, 3, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           anInsets, 0, 0));

    addUninstallAction();
    addUpgradeAction();

    add(myInstallButton,
        new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               anInsets, 0, 0));
    add(myUninstallButton,
        new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               anInsets, 0, 0));
    add(myUpgradeButton,
        new GridBagConstraints(1, 2, 1, 1, 0.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, anInsets, 0, 0));

    myPackagesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        updateUninstallUpgrade();
      }
    });

    myInstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myPackageManagementService != null) {
          ManagePackagesDialog dialog = createManagePackagesDialog();
          dialog.show();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (myPackageManagementService != null && myInstallButton.isEnabled()) {
          ManagePackagesDialog dialog = createManagePackagesDialog();
          Point p = e.getPoint();
          int row = myPackagesTable.rowAtPoint(p);
          int column = myPackagesTable.columnAtPoint(p);
          if (row >= 0 && column >= 0) {
            Object pkg = myPackagesTable.getValueAt(row, 0);
            if (pkg instanceof InstalledPackage) {
              dialog.selectPackage((InstalledPackage) pkg);
            }
          }
          dialog.show();
          return true;
        }
        return false;
      }
    }.installOn(myPackagesTable);
  }

  private ManagePackagesDialog createManagePackagesDialog() {
    return new ManagePackagesDialog(myProject,
                                    myPackageManagementService,
                                    new PackageManagementService.Listener() {
                                      @Override
                                      public void operationStarted(String packageName) {
                                        myPackagesTable.setPaintBusy(true);
                                      }

                                      @Override
                                      public void operationFinished(String packageName,
                                                                    @Nullable String errorDescription) {
                                        myNotificationArea.showResult(packageName, errorDescription);
                                        myPackagesTable.clearSelection();
                                        doUpdatePackages(myPackageManagementService);
                                      }
                                    });
  }

  public void addPathChangedListener(Consumer<Sdk> consumer) {
    myPathChangedListeners.add(consumer);
  }

  private void addUpgradeAction() {
    myUpgradeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final int[] rows = myPackagesTable.getSelectedRows();
        if (myPackageManagementService != null) {
          final Set<String> upgradedPackages = new HashSet<String>();
          final Set<String> packagesShouldBePostponed = getPackagesToPostpone();
          for (int row : rows) {
            final Object packageObj = myPackagesTableModel.getValueAt(row, 0);
            if (packageObj instanceof InstalledPackage) {
              InstalledPackage pkg = (InstalledPackage)packageObj;
              final String packageName = pkg.getName();
              final String currentVersion = pkg.getVersion();
              final String availableVersion = (String)myPackagesTableModel.getValueAt(row, 2);

              if (packagesShouldBePostponed.contains(packageName)) {
                myWaitingToUpgrade.add((InstalledPackage)packageObj);
              }
              else if (PackageVersionComparator.VERSION_COMPARATOR.compare(currentVersion, availableVersion) < 0) {
                upgradePackage(pkg, availableVersion);
                upgradedPackages.add(packageName);
              }
            }
          }

          if (myCurrentlyInstalling.isEmpty() && upgradedPackages.isEmpty() && !myWaitingToUpgrade.isEmpty()) {
            upgradePostponedPackages();
          }
        }
      }
    });
  }

  private void upgradePostponedPackages() {
    final Iterator<InstalledPackage> iterator = myWaitingToUpgrade.iterator();
    final InstalledPackage toUpgrade = iterator.next();
    iterator.remove();
    upgradePackage(toUpgrade, toUpgrade.getVersion());
  }

  protected Set<String> getPackagesToPostpone() {
    return Collections.emptySet();
  }

  private void upgradePackage(@NotNull final InstalledPackage pkg, @Nullable final String toVersion) {
    final PackageManagementService selPackageManagementService = myPackageManagementService;
    myPackageManagementService.fetchPackageVersions(pkg.getName(), new CatchingConsumer<List<String>, Exception>() {
      @Override
      public void consume(List<String> releases) {
        if (!releases.isEmpty() &&
            PackageVersionComparator.VERSION_COMPARATOR.compare(pkg.getVersion(), releases.get(0)) >= 0) {
          return;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            final PackageManagementService.Listener listener = new PackageManagementService.Listener() {
              @Override
              public void operationStarted(String packageName) {
                myPackagesTable.setPaintBusy(true);
                myCurrentlyInstalling.add(packageName);
              }

              @Override
              public void operationFinished(String packageName, @Nullable String errorDescription) {
                myPackagesTable.clearSelection();
                updatePackages(selPackageManagementService);
                myPackagesTable.setPaintBusy(false);
                myCurrentlyInstalling.remove(packageName);
                if (errorDescription == null) {
                  myNotificationArea.showSuccess("Package " + packageName + " successfully upgraded");
                }
                else {
                  myNotificationArea.showError("Upgrade packages failed. <a href=\"xxx\">Details...</a>",
                                               "Upgrade Packages Failed",
                                               "Upgrade packages failed.\n" + errorDescription);
                }

                if (myCurrentlyInstalling.isEmpty() && !myWaitingToUpgrade.isEmpty()) {
                  upgradePostponedPackages();
                }
              }
            };
            PackageManagementServiceEx serviceEx = getServiceEx();
            if (serviceEx != null) {
              serviceEx.updatePackage(pkg, toVersion, listener);
            }
            else {
              myPackageManagementService.installPackage(new RepoPackage(pkg.getName(), null /* TODO? */), null, true, null, listener, false);
            }
            myUpgradeButton.setEnabled(false);
          }
        }, ModalityState.any());
      }

      @Override
      public void consume(Exception e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog("Error occurred. Please, check your internet connection.",
                                     "Upgrade Package Failed.");
          }
        }, ModalityState.any());
      }
    });
  }

  @Nullable
  private PackageManagementServiceEx getServiceEx() {
    return ObjectUtils.tryCast(myPackageManagementService, PackageManagementServiceEx.class);
  }

  private void updateUninstallUpgrade() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final int[] selected = myPackagesTable.getSelectedRows();
        boolean upgradeAvailable = false;
        boolean canUninstall = true;
        boolean canUpgrade = true;
        if (myPackageManagementService != null && selected.length != 0) {
          for (int i = 0; i != selected.length; ++i) {
            final int index = selected[i];
            if (index >= myPackagesTable.getRowCount()) continue;
            final Object value = myPackagesTable.getValueAt(index, 0);
            if (value instanceof InstalledPackage) {
              final InstalledPackage pkg = (InstalledPackage)value;
              if (!canUninstallPackage(pkg)) {
                canUninstall = false;
              }
              if (!canUpgradePackage(pkg)) {
                canUpgrade = false;
              }
              final String pyPackageName = pkg.getName();
              final String availableVersion = (String)myPackagesTable.getValueAt(index, 2);
              if (!upgradeAvailable) {
                upgradeAvailable = PackageVersionComparator.VERSION_COMPARATOR.compare(pkg.getVersion(), availableVersion) < 0 &&
                                   !myCurrentlyInstalling.contains(pyPackageName);
              }
              if (!canUninstall && !canUpgrade) break;
            }
          }
        }
        myUninstallButton.setEnabled(canUninstall);
        myUpgradeButton.setEnabled(upgradeAvailable && canUpgrade);
      }
    }, ModalityState.any());
  }

  protected boolean canUninstallPackage(InstalledPackage pyPackage) {
    return true;
  }

  protected boolean canUpgradePackage(InstalledPackage pyPackage) {
    return true;
  }

  private void addUninstallAction() {
    myUninstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final List<InstalledPackage> packages = getSelectedPackages();
        final PackageManagementService selPackageManagementService = myPackageManagementService;
        if (selPackageManagementService != null) {
          PackageManagementService.Listener listener = new PackageManagementService.Listener() {
            @Override
            public void operationStarted(String packageName) {
              myPackagesTable.setPaintBusy(true);
            }

            @Override
            public void operationFinished(String packageName, @Nullable String errorDescription) {
              myPackagesTable.clearSelection();
              updatePackages(selPackageManagementService);
              myPackagesTable.setPaintBusy(false);
              if (errorDescription == null) {
                if (packageName != null) {
                  myNotificationArea.showSuccess("Package '" + packageName + "' successfully uninstalled");
                }
                else {
                  myNotificationArea.showSuccess("Packages successfully uninstalled");
                }
              }
              else {
                myNotificationArea.showError("Uninstall packages failed. <a href=\"xxx\">Details...</a>",
                                             "Uninstall Packages Failed",
                                             "Uninstall packages failed.\n" + errorDescription);
              }
            }
          };
          myPackageManagementService.uninstallPackages(packages, listener);
        }
      }
    });
  }

  @NotNull
  private List<InstalledPackage> getSelectedPackages() {
    final List<InstalledPackage> results = new ArrayList<InstalledPackage>();
    final int[] rows = myPackagesTable.getSelectedRows();
    for (int row : rows) {
      final Object packageName = myPackagesTableModel.getValueAt(row, 0);
      if (packageName instanceof InstalledPackage) {
        results.add((InstalledPackage)packageName);
      }
    }
    return results;
  }

  public void updatePackages(@Nullable PackageManagementService packageManagementService) {
    myPackageManagementService = packageManagementService;
    myPackagesTable.clearSelection();
    myPackagesTableModel.getDataVector().clear();
    doUpdatePackages(packageManagementService);
  }

  public void doUpdatePackages(final PackageManagementService packageManagementService) {
    myPackagesTable.setPaintBusy(true);
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        Collection<InstalledPackage> packages = Lists.newArrayList();
        if (packageManagementService != null) {
          try {
            packages = packageManagementService.getInstalledPackages();
          }
          catch (IOException e) {
            // do nothing, we already have an empty list
          }
          finally {
            final Collection<InstalledPackage> finalPackages = packages;

            final Map<String, RepoPackage> cache = buildNameToPackageMap(packageManagementService.getAllPackagesCached());
            if (cache.isEmpty()) {
              refreshLatestVersions();
            }
            application.invokeLater(new Runnable() {
              @Override
              public void run() {
                if (packageManagementService == myPackageManagementService) {
                  myPackagesTableModel.getDataVector().clear();
                  for (InstalledPackage pkg : finalPackages) {
                    RepoPackage repoPackage = cache.get(pkg.getName());
                    final String version = repoPackage != null ? repoPackage.getLatestVersion() : null;
                    myPackagesTableModel
                      .addRow(new Object[]{pkg, pkg.getVersion(), version == null ? "" : version});
                  }
                  if (!cache.isEmpty()) {
                    myPackagesTable.setPaintBusy(false);
                  }
                }
              }
            }, ModalityState.any());
          }
        }
      }
    });
  }

  private void refreshLatestVersions() {
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          List<RepoPackage> packages = myPackageManagementService.reloadAllPackages();
          final Map<String, RepoPackage> packageMap = buildNameToPackageMap(packages);
          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              for (int i = 0; i != myPackagesTableModel.getRowCount(); ++i) {
                final InstalledPackage pyPackage = (InstalledPackage)myPackagesTableModel.getValueAt(i, 0);
                final RepoPackage repoPackage = packageMap.get(pyPackage.getName());
                myPackagesTableModel.setValueAt(repoPackage == null ? null : repoPackage.getLatestVersion(), i, 2);
              }
              myPackagesTable.setPaintBusy(false);
            }
          }, ModalityState.stateForComponent(myPackagesTable));
        }
        catch (IOException ignored) {
          myPackagesTable.setPaintBusy(false);
        }
      }
    });
  }

  private static Map<String, RepoPackage> buildNameToPackageMap(List<RepoPackage> packages) {
    final Map<String, RepoPackage> packageMap = new HashMap<String, RepoPackage>();
    for (RepoPackage aPackage : packages) {
      packageMap.put(aPackage.getName(), aPackage);
    }
    return packageMap;
  }

  private static class MyTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
                                                   final boolean hasFocus, final int row, final int column) {
      final JLabel cell = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final String version = (String)table.getValueAt(row, 1);
      final String availableVersion = (String)table.getValueAt(row, 2);
      cell.setIcon(PackageVersionComparator.VERSION_COMPARATOR.compare(version, availableVersion) < 0 && column == 2 ?
                   AllIcons.Vcs.Arrow_right : null);
      final Object pyPackage = table.getValueAt(row, 0);
      if (pyPackage instanceof InstalledPackage) {
        cell.setToolTipText(((InstalledPackage) pyPackage).getTooltipText());
      }
      return cell;
    }
  }
}
