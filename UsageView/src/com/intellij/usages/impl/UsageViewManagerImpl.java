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
      true);
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
      new Runnable() {
        private int myUsageCount = 0;
        private Usage myFirstUsage = null;

        private void activateView() {
          if (usageView[0] != null) return;

          usageView[0] = new UsageViewImpl(presentation, searchFor, searcherFactory, myProject);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              addContent(usageView[0], presentation);
              activateToolwindow();
            }
          });
        }

        public void run() {
          if (!showNotFoundMessage) activateView();
          UsageSearcher usageSearcher = searcherFactory.create();
          usageSearcher.generate(new Processor<Usage>() {
            public boolean process(final Usage usage) {
              myUsageCount++;
              if (myUsageCount == 1 && !showPanelIfOnlyOneUsage) {
                myFirstUsage = usage;
              }
              if (myUsageCount == 2 ||
                  (showPanelIfOnlyOneUsage && myUsageCount == 1)) {
                activateView();
                if (myFirstUsage != null) {
                  usageView[0].appendUsageLater(myFirstUsage);
                }
                usageView[0].appendUsageLater(usage);
              }
              else if (myUsageCount > 2) {
                usageView[0].appendUsageLater(usage);
              }

              ProgressIndicator indicator = ProgressManager.getInstance()
                .getProgressIndicator();
              return indicator != null
                     ? !indicator.isCanceled()
                     : true;
            }
          });

          if (myUsageCount == 0 && showNotFoundMessage) {
            application.invokeLater(new Runnable() {
              public void run() {
                Messages.showMessageDialog(myProject, "No " + presentation.getUsagesString() + " found in " + presentation.getScopeText(), "Information",
                                       Messages.getInformationIcon());
              }
            }, ModalityState.NON_MMODAL);
          }
          else if (myUsageCount == 1 && !showPanelIfOnlyOneUsage) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myFirstUsage.navigate(true);
              }
            });
          }
          else {
            usageView[0].setSearchInProgress(false);
          }
        }
      }, getProgressTitile(presentation), true, myProject);

    return usageView[0];
  }

  static String getProgressTitile(UsageViewPresentation presentation) {
    return "Searching for " + presentation.getUsagesString() + " in " + presentation.getScopeText() + "...";
  }

  private void activateToolwindow() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    if (!toolWindow.isActive()) toolWindow.activate(null);
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

}
