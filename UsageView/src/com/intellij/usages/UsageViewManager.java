package com.intellij.usages;


import com.intellij.openapi.util.Factory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:14:03 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class UsageViewManager {
  public static UsageViewManager getInstance (Project project) {
    return project.getComponent(UsageViewManager.class);
  }

  @NotNull
  public abstract UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation);

  @NotNull
  public abstract UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation);

  @Nullable (documentation = "in case no usages found or usage view not shown for one usage")
  public abstract UsageView searchAndShowUsages(UsageTarget[] searchFor,
                                Factory<UsageSearcher> searcherFactory,
                                boolean showPanelIfOnlyOneUsage,
                                boolean showNotFoundMessage, UsageViewPresentation presentation,
                                UsageViewStateListener listener);

  public interface UsageViewStateListener {
    void usageViewCreated(UsageView usageView);
    void findingUsagesFinished(final UsageView usageView);
  }

  public abstract void searchAndShowUsages(UsageTarget[] searchFor,
                           Factory<UsageSearcher> searcherFactory,
                           FindUsagesProcessPresentation processPresentation,
                           UsageViewPresentation presentation,
                           UsageViewStateListener listener);

  @Nullable
  public abstract UsageView getSelectedUsageView();
}
