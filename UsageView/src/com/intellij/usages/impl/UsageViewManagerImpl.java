package com.intellij.usages.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.usages.*;
import com.intellij.util.Processor;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:49:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewManagerImpl implements UsageViewManager, ProjectComponent {
  private Project myProject;

  public UsageViewManagerImpl(Project project) {
    myProject = project;
  }

  public UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation) {
    UsageViewImpl usageView = new UsageViewImpl(presentation, targets, null, myProject);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  public UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation);
    addContent((UsageViewImpl)usageView, presentation);
    activateToolwindow();
    return usageView;
  }

  private Content addContent(UsageViewImpl usageView, UsageViewPresentation presentation) {
    Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).addContent(
      presentation.getTabText(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab(),
      true
    );
    usageView.setContent(content);
    return content;
  }

  public UsageView searchAndShowUsages(final UsageTarget[] searchFor,
                                       final Factory<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage, final UsageViewPresentation presentation) {

    final UsageViewImpl[] usageView = new UsageViewImpl[]{null};

    final Application application = ApplicationManager.getApplication();
    application.runProcessWithProgressSynchronously(
      createSearchRunnable(usageView, presentation, searchFor, searcherFactory, showNotFoundMessage, showPanelIfOnlyOneUsage, null),
      getProgressTitle(presentation),
      true,
      myProject
    );

    return usageView[0];
  }

  private Runnable createSearchRunnable(final UsageViewImpl[] usageView,
                                        final UsageViewPresentation presentation,
                                        final UsageTarget[] searchFor,
                                        final Factory<UsageSearcher> searcherFactory,
                                        final boolean showNotFoundMessage,
                                        final boolean showPanelIfOnlyOneUsage,
                                        final UsageViewManager.UsageViewStateListener listener) {
    return new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, showNotFoundMessage, showPanelIfOnlyOneUsage, listener);
  }

  public void searchAndShowUsages(UsageTarget[] searchFor,
                                       Factory<UsageSearcher> searcherFactory,
                                       boolean showPanelIfOnlyOneUsage,
                                       boolean showNotFoundMessage,
                                       UsageViewPresentation presentation,
                                       final Factory<ProgressIndicator> progressIndicatorFactory,
                                       UsageViewManager.UsageViewStateListener listener
                                       ) {
    final UsageViewImpl[] usageView = new UsageViewImpl[]{null};
    final SearchForUsagesRunnable runnable = (SearchForUsagesRunnable)createSearchRunnable(
      usageView, presentation, searchFor, searcherFactory, showNotFoundMessage, showPanelIfOnlyOneUsage, listener
    );

    UsageViewImplUtil.runProcessWithProgress(
      progressIndicatorFactory.create(),
      new Runnable() {
        public void run() {
          runnable.searchUsages();
          if (usageView[0] != null) usageView[0].setProgressIndicatorFactory(progressIndicatorFactory);
        }
      },
      new Runnable() {
        public void run() {
          runnable.endSearchForUsages();
        }
      }
    );
  }

  static String getProgressTitle(UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    if (scopeText == null) return "Searching for " + presentation.getUsagesString() + "...";
    return "Searching for " + presentation.getUsagesString() + " in " + scopeText + "...";
  }

  private void activateToolwindow() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    if (!toolWindow.isActive()) toolWindow.show(null);
  }

  private void appendUsages(Usage[] foundUsages, UsageViewImpl usageView) {
    for (int i = 0; i < foundUsages.length; i++) {
      Usage usage = foundUsages[i];
      usageView.appendUsage(usage);
    }
  }


  public String getComponentName() {
    return "NewUsageViewManager";
  }

  public void initComponent() { }

  public void disposeComponent() { }

  public void projectOpened() { }

  public void projectClosed() { }

  private class SearchForUsagesRunnable implements Runnable {
    private int myUsageCount = 0;
    private Usage myFirstUsage = null;
    private final UsageViewImpl[] myUsageView;
    private final UsageViewPresentation myPresentation;
    private final UsageTarget[] mySearchFor;
    private final Factory<UsageSearcher> mySearcherFactory;
    private final boolean myShowNotFoundMessage;
    private final boolean myShowPanelIfOnlyOneUsage;
    private UsageViewManager.UsageViewStateListener myListener;

    public SearchForUsagesRunnable(final UsageViewImpl[] usageView,
                   final UsageViewPresentation presentation,
                   final UsageTarget[] searchFor,
                   final Factory<UsageSearcher> searcherFactory,
                   final boolean showNotFoundMessage,
                   final boolean showPanelIfOnlyOneUsage,
                   final UsageViewManager.UsageViewStateListener listener) {
      myUsageView = usageView;
      myPresentation = presentation;
      mySearchFor = searchFor;
      mySearcherFactory = searcherFactory;
      myShowNotFoundMessage = showNotFoundMessage;
      myShowPanelIfOnlyOneUsage = showPanelIfOnlyOneUsage;
      myListener = listener;
    }

    private void activateView() {
      if (myUsageView[0] != null) return;

      myUsageView[0] = new UsageViewImpl(myPresentation, mySearchFor, mySearcherFactory, myProject);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          addContent(myUsageView[0], myPresentation);
          if (myListener!=null) myListener.usageViewCreated(myUsageView[0]);
          activateToolwindow();
        }
      });
    }

    public void run() {
      searchUsages();

      endSearchForUsages();
    }

    private void searchUsages() {
      if (!myShowNotFoundMessage) activateView();
      UsageSearcher usageSearcher = mySearcherFactory.create();
      usageSearcher.generate(new Processor<Usage>() {
        public boolean process(final Usage usage) {
          myUsageCount++;
          if (myUsageCount == 1 && !myShowPanelIfOnlyOneUsage) {
            myFirstUsage = usage;
          }
          if (myUsageCount == 2 ||
              (myShowPanelIfOnlyOneUsage && myUsageCount == 1)) {
            activateView();
            if (myFirstUsage != null) {
              myUsageView[0].appendUsageLater(myFirstUsage);
            }
            myUsageView[0].appendUsageLater(usage);
          }
          else if (myUsageCount > 2) {
            myUsageView[0].appendUsageLater(usage);
          }

          ProgressIndicator indicator = ProgressManager.getInstance()
            .getProgressIndicator();
          return indicator != null
                 ? !indicator.isCanceled()
                 : true;
        }
      });
    }

    private void endSearchForUsages() {
      if (myUsageCount == 0 && myShowNotFoundMessage) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(myProject, "No " + myPresentation.getUsagesString() + " found in " + myPresentation.getScopeText(), "Information",
                                   Messages.getInformationIcon());
          }
        }, ModalityState.NON_MMODAL);
      }
      else if (myUsageCount == 1 && !myShowPanelIfOnlyOneUsage) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myFirstUsage.canNavigate()) {
              myFirstUsage.navigate(true);
            }
          }
        });
      }
      else {
        myUsageView[0].setSearchInProgress(false);
      }

      if (myListener != null) {
        myListener.findingUsagesFinished();
      }
    }
  }
}
