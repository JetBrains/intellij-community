package com.intellij.usages;


import com.intellij.openapi.util.Factory;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:14:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageViewManager {
  UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation);

  UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation);

  UsageView searchAndShowUsages(UsageTarget[] searchFor,
                                Factory<UsageSearcher> searcherFactory,
                                boolean showPanelIfOnlyOneUsage,
                                boolean showNotFoundMessage, UsageViewPresentation presentation);

  interface UsageViewStateListener {
    void usageViewCreated(UsageView usageView);
    void findingUsagesFinished();
  }

  void searchAndShowUsages(UsageTarget[] searchFor,
                           Factory<UsageSearcher> searcherFactory,
                           boolean showPanelIfOnlyOneUsage,
                           boolean showNotFoundMessage,
                           UsageViewPresentation presentation,
                           Factory<ProgressIndicator> progressIndicatorFactory,
                           UsageViewStateListener listener);
}
