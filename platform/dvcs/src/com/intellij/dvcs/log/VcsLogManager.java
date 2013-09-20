package com.intellij.dvcs.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogManager extends AbstractProjectComponent {

  public static final ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");

  @NotNull private final ProjectLevelVcsManager myVcsManager;

  protected VcsLogManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManagerInitializedFirst) {
    super(project);
    myVcsManager = vcsManagerInitializedFirst;
  }

  @Override
  public void initComponent() {
    super.initComponent();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        if (!Registry.is("git.new.log")) {
          return;
        }

        final Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders();
        if (logProviders.isEmpty()) {
          return;
        }

        final VcsLogContainer mainPanel = new VcsLogContainer(myProject);
        Content vcsLogContentPane = new ContentImpl(mainPanel, "Log", true);
        ChangesViewContentI changesView = ChangesViewContentManager.getInstance(myProject);
        changesView.addContent(vcsLogContentPane);
        vcsLogContentPane.setCloseable(false);

        VcsLogDataHolder.init(myProject, logProviders, new Consumer<VcsLogDataHolder>() {
          @Override
          public void consume(VcsLogDataHolder vcsLogDataHolder) {
            Disposer.register(myProject, vcsLogDataHolder);
            VcsLogUI logUI = new VcsLogUI(vcsLogDataHolder, myProject, new VcsLogColorManagerImpl(logProviders.keySet()));
            mainPanel.init(logUI.getMainFrame().getMainComponent());
            refreshLogOnVcsEvents(vcsLogDataHolder, logProviders);
          }
        });

      }
    });
  }

  private static void refreshLogOnVcsEvents(@NotNull final VcsLogDataHolder vcsLogDataHolder,
                                            @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      providers2roots.putValue(entry.getValue(), entry.getKey());
    }

    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), new VcsLogRefresher() {
        @Override
        public void refresh(@NotNull VirtualFile root) {
          vcsLogDataHolder.refresh(root);
        }

        @Override
        public void refreshRefs(@NotNull VirtualFile root) {
          vcsLogDataHolder.refreshRefs(root);
        }
      });
    }
  }

  @NotNull
  private Map<VirtualFile, VcsLogProvider> findLogProviders() {
    Map<VirtualFile, VcsLogProvider> logProviders = ContainerUtil.newHashMap();
    VcsLogProvider[] allLogProviders = Extensions.getExtensions(LOG_PROVIDER_EP, myProject);
    for (AbstractVcs vcs : myVcsManager.getAllActiveVcss()) {
      for (VcsLogProvider provider : allLogProviders) {
        if (provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod())) {
          for (VirtualFile root : myVcsManager.getRootsUnderVcs(vcs)) {
            logProviders.put(root, provider);
          }
          break;
        }
      }
    }
    return logProviders;
  }

  private static class VcsLogContainer extends JPanel {

    private final JBLoadingPanel myLoadingPanel;

    VcsLogContainer(@NotNull Disposable disposable) {
      setLayout(new BorderLayout());
      myLoadingPanel = new JBLoadingPanel(new BorderLayout(), disposable);
      add(myLoadingPanel);
      myLoadingPanel.startLoading();
    }

    void init(@NotNull JComponent mainComponent) {
      myLoadingPanel.add(mainComponent);
      myLoadingPanel.stopLoading();
    }
  }

}
