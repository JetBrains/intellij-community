package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UsagesPanel extends JPanel implements Disposable, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.ui.UsagesPanel");

  private Project myProject;
  private List<DependenciesBuilder> myBuilders;
  private ProgressIndicator myCurrentProgress;
  private JComponent myCurrentComponent;
  private UsageView myCurrentUsageView;
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public UsagesPanel(Project project, DependenciesBuilder  builder) {
    this(project, Collections.singletonList(builder));
  }

  public UsagesPanel(Project project, List<DependenciesBuilder> builders) {
    super(new BorderLayout());
    myProject = project;
    myBuilders = builders;
    setToInitialPosition();
  }

  public void setToInitialPosition() {
    cancelCurrentFindRequest();
    setToComponent(createLabel(myBuilders.get(0).getInitialUsagesPosition()));
  }

  public void findUsages(final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    cancelCurrentFindRequest();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            final ProgressIndicator progress = new PanelProgressIndicator(new Consumer<JComponent>() {
              public void consume(final JComponent component) {
                setToComponent(component);
              }
            });
            myCurrentProgress = progress;
            ProgressManager.getInstance().runProcess(new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    UsageInfo[] usages = new UsageInfo[0];
                    Set<PsiFile> elementsToSearch = null;

                    try {
                      if (myBuilders.get(0).isBackward()){
                        elementsToSearch = searchIn;
                        usages = FindDependencyUtil.findBackwardDependencies(myBuilders, searchFor, searchIn);
                      }
                      else {
                        elementsToSearch = searchFor;
                        usages = FindDependencyUtil.findDependencies(myBuilders, searchIn, searchFor);
                      }
                    }
                    catch (ProcessCanceledException e) {
                    }
                    catch (Exception e) {
                      LOG.error(e);
                    }

                    if (!progress.isCanceled()) {
                      final UsageInfo[] finalUsages = usages;
                      final PsiElement[] _elementsToSearch = elementsToSearch != null? elementsToSearch.toArray(new PsiElement[elementsToSearch.size()]) : PsiElement.EMPTY_ARRAY;
                      ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                          showUsages(new UsageInfoToUsageConverter.TargetElementsDescriptor(_elementsToSearch), finalUsages);
                        }
                      }, ModalityState.stateForComponent(UsagesPanel.this));
                    }
                  }
                });
                myCurrentProgress = null;
              }
            }, progress);
          }
        });
      }
    }, 300);
  }

  private void cancelCurrentFindRequest() {
    if (myCurrentProgress != null) {
      myCurrentProgress.cancel();
    }
  }

  private void showUsages(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor, final UsageInfo[] usageInfos) {
    try {
      Usage[] usages = UsageInfoToUsageConverter.convert(descriptor, usageInfos);
      UsageViewPresentation presentation = new UsageViewPresentation();
      presentation.setCodeUsagesString(myBuilders.get(0).getRootNodeNameInUsageView());
      myCurrentUsageView = UsageViewManager.getInstance(myProject).createUsageView(new UsageTarget[0], usages, presentation, null);
      setToComponent(myCurrentUsageView.getComponent());
    }
    catch (ProcessCanceledException e) {
      setToCanceled();
    }
  }

  private void setToCanceled() {
    setToComponent(createLabel(AnalysisScopeBundle.message("usage.view.canceled")));
  }

  private void setToComponent(final JComponent cmp) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myCurrentComponent != null) {
          if (myCurrentUsageView != null && myCurrentComponent == myCurrentUsageView.getComponent()){
            myCurrentUsageView.dispose();
          }
          remove(myCurrentComponent);
        }
        myCurrentComponent = cmp;
        add(cmp, BorderLayout.CENTER);
        revalidate();
      }
    });
  }

  public void dispose(){
    if (myCurrentUsageView != null){
      Disposer.dispose(myCurrentUsageView);
    }
  }

  public static JComponent createLabel(String text) {
    JLabel label = new JLabel(text);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    return label;
  }

  @Nullable
  @NonNls
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(DataConstants.HELP_ID)) {
      return "ideaInterface.find";
    }
    return null;
  }

  public void addBuilder(DependenciesBuilder builder) {
    myBuilders.add(builder);
  }
}
