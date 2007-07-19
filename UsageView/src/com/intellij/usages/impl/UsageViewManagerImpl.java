/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.find.SearchInBackgroundOption;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.ui.RangeBlinker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class UsageViewManagerImpl extends UsageViewManager {
  private Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");
  private volatile boolean mySearchHasBeenCancelled;

  public UsageViewManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation, Factory<UsageSearcher> usageSearcherFactory) {
    UsageViewImpl usageView = new UsageViewImpl(presentation, targets, usageSearcherFactory, myProject);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @NotNull
  public UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation, Factory<UsageSearcher> factory) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation, factory);
    addContent((UsageViewImpl)usageView, presentation);
    showToolWindow(true);
    return usageView;
  }

  @NotNull
  public UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  private void addContent(UsageViewImpl usageView, UsageViewPresentation presentation) {
    Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).addContent(
      presentation.getTabText(),
      presentation.getTabName(),
      presentation.getToolwindowTitle(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab(),
      true
    );
    usageView.setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
  }

  public UsageView searchAndShowUsages(final UsageTarget[] searchFor,
                                       final Factory<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage, final UsageViewPresentation presentation,
                                       final UsageViewStateListener listener) {
    final AtomicReference<UsageViewImpl> usageView = new AtomicReference<UsageViewImpl>();

    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    Task task = new Task.Backgroundable(myProject, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      public void run(final ProgressIndicator indicator) {
        new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener).run();
      }
    };
    ProgressManager.getInstance().run(task);
    return usageView.get();
  }

  public void searchAndShowUsages(UsageTarget[] searchFor,
                                  Factory<UsageSearcher> searcherFactory,
                                  FindUsagesProcessPresentation processPresentation,
                                  UsageViewPresentation presentation,
                                  UsageViewManager.UsageViewStateListener listener
                                       ) {
    final AtomicReference<UsageViewImpl> usageView = new AtomicReference<UsageViewImpl>();
    final SearchForUsagesRunnable runnable = new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener);
    final Factory<ProgressIndicator> progressIndicatorFactory = processPresentation.getProgressIndicatorFactory();

    UsageViewImplUtil.runProcessWithProgress(progressIndicatorFactory != null ? progressIndicatorFactory.create() : null,
      new Runnable() {
        public void run() {
          runnable.searchUsages();
        }
      },
      new Runnable() {
        public void run() {
          runnable.endSearchForUsages();
        }
      }
    );
  }

  public UsageView getSelectedUsageView() {
    final Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  public static String getProgressTitle(UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    if (scopeText == null) {
      return UsageViewBundle.message("progress.searching.for", StringUtil.capitalize(presentation.getUsagesString()));
    }
    return UsageViewBundle.message("progress.searching.for.in", StringUtil.capitalize(presentation.getUsagesString()), scopeText);
  }

  private void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  private static void appendUsages(final Usage[] foundUsages, final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (Usage foundUsage : foundUsages) {
          usageView.appendUsage(foundUsage);
        }
      }
    });
  }


  public synchronized void setCurrentSearchCancelled(boolean flag) {
    mySearchHasBeenCancelled = flag;
  }

  public synchronized boolean searchHasBeenCancelled() {
    return mySearchHasBeenCancelled;
  }

  private class SearchForUsagesRunnable implements Runnable {
    private final AtomicInteger myUsageCount = new AtomicInteger(0);
    private final AtomicReference<Usage> myFirstUsage = new AtomicReference<Usage>();
    private final AtomicReference<UsageViewImpl> myUsageViewRef;
    private final UsageViewPresentation myPresentation;
    private final UsageTarget[] mySearchFor;
    private final Factory<UsageSearcher> mySearcherFactory;
    private final FindUsagesProcessPresentation myProcessPresentation;
    private final UsageViewStateListener myListener;

    private SearchForUsagesRunnable(@NotNull final AtomicReference<UsageViewImpl> usageView,
                                   @NotNull final UsageViewPresentation presentation,
                                   @NotNull final UsageTarget[] searchFor,
                                   @NotNull final Factory<UsageSearcher> searcherFactory,
                                   @NotNull FindUsagesProcessPresentation processPresentation,
                                   final UsageViewManager.UsageViewStateListener listener) {
      myUsageViewRef = usageView;
      myPresentation = presentation;
      mySearchFor = searchFor;
      mySearcherFactory = searcherFactory;
      myProcessPresentation = processPresentation;
      myListener = listener;
      mySearchHasBeenCancelled = false;
    }

    private UsageViewImpl getUsageView() {
      UsageViewImpl usageView = myUsageViewRef.get();
      if (usageView != null) return usageView;
      int usageCount = myUsageCount.get();
      if (usageCount >= 2 || usageCount == 1 && myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        usageView = new UsageViewImpl(myPresentation, mySearchFor, mySearcherFactory, myProject);
        if (myUsageViewRef.compareAndSet(null, usageView)) {
          openView(usageView);
          Usage firstUsage = myFirstUsage.get();
          if (firstUsage != null) {
            usageView.appendUsageLater(firstUsage);
          }
        }
        return myUsageViewRef.get();
      }
      return null;
    }

    private void openView(final UsageViewImpl usageView) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          addContent(usageView, myPresentation);
          if (myListener!=null) {
            myListener.usageViewCreated(usageView);
          }
          showToolWindow(false);
        }
      });
    }

    public void run() {
      searchUsages();
      endSearchForUsages();
    }

    private void searchUsages() {
      UsageSearcher usageSearcher = mySearcherFactory.create();
      usageSearcher.generate(new Processor<Usage>() {
        public boolean process(final Usage usage) {
          if (mySearchHasBeenCancelled) return false;
          int usageCount = myUsageCount.incrementAndGet();
          if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
            myFirstUsage.compareAndSet(null,usage);
          }
          UsageViewImpl usageView = getUsageView();
          if (usageView != null) {
            usageView.appendUsageLater(usage);
          }
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          return indicator == null || !indicator.isCanceled();
        }
      });
      if (getUsageView() != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showToolWindow(true);
          }
        });
      }
    }

    private void endSearchForUsages() {
      int usageCount = myUsageCount.get();
      if (usageCount == 0 && myProcessPresentation.isShowNotFoundMessage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final List<Action> notFoundActions = myProcessPresentation.getNotFoundActions();
            final String message = UsageViewBundle.message("dialog.no.usages.found.in",
                                                           StringUtil.decapitalize(myPresentation.getUsagesString()),
                                                           myPresentation.getScopeText());

            if (notFoundActions == null || notFoundActions.isEmpty()) {
              Messages.showMessageDialog(myProject, message, UsageViewBundle.message("dialog.title.information"), Messages.getInformationIcon());
            }
            else {
              List<String> titles = new ArrayList<String>(notFoundActions.size() + 1);
              titles.add(UsageViewBundle.message("dialog.button.ok"));
              for (Action action : notFoundActions) {
                Object value = action.getValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY);
                if (value == null) value = action.getValue(Action.NAME);

                titles.add((String)value);
              }

              int option = Messages.showDialog(myProject, message, UsageViewBundle.message("dialog.title.information"),
                                               titles.toArray(new String[titles.size()]), 0, Messages.getInformationIcon());

              if (option > 0) {
                notFoundActions.get(option - 1).actionPerformed(new ActionEvent(this, 0, titles.get(option)));
              }
            }
          }
        }, ModalityState.NON_MODAL);
      }
      else if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            Usage usage = myFirstUsage.get();
            if (usage.canNavigate()) {
              usage.navigate(true);
              flashUsageScriptaculously(usage);
            }
          }
        });
      }
      else {
        final UsageViewImpl usageView = myUsageViewRef.get();
        if (usageView != null) usageView.setSearchInProgress(false);
      }

      if (myListener != null) {
        myListener.findingUsagesFinished(myUsageViewRef.get());
      }
    }
  }

  private static void flashUsageScriptaculously(final Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) {
      return;
    }
    UsageInfo2UsageAdapter usageInfo = (UsageInfo2UsageAdapter)usage;

    Editor editor = usageInfo.openTextEditor(true);
    if (editor == null) return;
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    RangeBlinker rangeBlinker = new RangeBlinker(editor, attributes, 6);
    rangeBlinker.resetMarkers(usageInfo.getRangeMarkers());
    rangeBlinker.startBlinking();
  }
}
