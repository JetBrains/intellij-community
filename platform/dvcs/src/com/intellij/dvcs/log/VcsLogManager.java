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
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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

        final VcsLogContainer mainPanel = new VcsLogContainer(myProject);
        Content vcsLogContentPane = new ContentImpl(mainPanel, "Log", true);
        ChangesViewContentI changesView = ChangesViewContentManager.getInstance(myProject);
        changesView.addContent(vcsLogContentPane);
        vcsLogContentPane.setCloseable(false);

        // TODO multi-roots (including Git + Hg roots within a single project & multiple providers (or just pass root in params)
        VcsLogProvider logProvider = null;
        for (VcsLogProvider provider : Extensions.getExtensions(LOG_PROVIDER_EP, myProject)) {
          logProvider = provider;
        }
        VirtualFile root = myVcsManager.getAllVcsRoots()[0].getPath();

        VcsLogDataHolder.init(myProject, logProvider, root, new Consumer<VcsLogDataHolder>() {
          @Override
          public void consume(VcsLogDataHolder vcsLogDataHolder) {
            Disposer.register(myProject, vcsLogDataHolder);
            myProject.getMessageBus().connect(myProject).subscribe(VcsLogRefresher.TOPIC, vcsLogDataHolder);
            VcsLogUI logUI = new VcsLogUI(vcsLogDataHolder, myProject);
            mainPanel.init(logUI.getMainFrame().getMainComponent());
          }
        });

      }
    });
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
