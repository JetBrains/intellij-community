package com.intellij.dvcs.log;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.vcs.log.VcsLogProvider;
import org.hanuna.gitalk.swing_ui.Swing_UI;
import org.hanuna.gitalk.ui.impl.UI_ControllerImpl;
import org.jetbrains.annotations.NotNull;

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

        // TODO run in background (currently run under modal progress: see com.intellij.ide.startup.impl.StartupManagerImpl#runActivities

        // TODO multi-roots (including Git + Hg roots within a single project & multiple providers (or just pass root in params)
        VcsLogProvider logProvider = null;
        for (VcsLogProvider provider : Extensions.getExtensions(LOG_PROVIDER_EP, myProject)) {
          logProvider = provider;
        }
        VirtualFile root = myVcsManager.getAllVcsRoots()[0].getPath();


        UI_ControllerImpl myUiController = new UI_ControllerImpl(myProject, logProvider, root);
        Swing_UI mySwingUi = new Swing_UI(myUiController);
        myUiController.addControllerListener(mySwingUi.getControllerListener());
        myUiController.init(false, false);

        Content vcsLogContentPane = new ContentImpl(mySwingUi.getMainFrame().getMainComponent(), "VCS LOG", true);
        ChangesViewContentI changesView = ChangesViewContentManager.getInstance(myProject);
        changesView.addContent(vcsLogContentPane);

      }
    });

    /*
    TODO refresh on repo changes

        myProject.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
          @Override
          public void repositoryChanged(@NotNull GitRepository repository) {
            myUiController.refresh(false);
          }
        });
     */
  }

}
