// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.wizard.Step;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.UiUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.welcomeScreen.ActionGroupPanelWrapper;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.UiNotifyConnector;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter.NPW_PREFIX;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractNewProjectDialog extends DialogWrapper {
  private Pair<JPanel, JBList<AnAction>> myPair;

  public AbstractNewProjectDialog() {
    super(ProjectManager.getInstance().getDefaultProject());
    init();
  }

  @Override
  protected void init() {
    super.init();
    DialogWrapperPeer peer = getPeer();
    JRootPane pane = peer.getRootPane();
    if (pane != null) {
      JBDimension size = JBUI.size(FlatWelcomeFrame.MAX_DEFAULT_WIDTH, FlatWelcomeFrame.DEFAULT_HEIGHT);
      pane.setMinimumSize(size);
      pane.setPreferredSize(size);
    }
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    setTitle(AbstractNewProjectStep.EP_NAME.hasAnyExtensions() ? ProjectBundle.message("dialog.title.new.project")
                                                                  : ProjectBundle.message("dialog.title.create.project"));
    DefaultActionGroup root = createRootStep();
    Disposer.register(getDisposable(), () -> root.removeAll());

    Pair<JPanel, JBList<AnAction>> pair = ActionGroupPanelWrapper.createActionGroupPanel(root, null, getDisposable());
    if (root instanceof AbstractNewProjectStep<?> projectStep) {
      projectStep.setWizardContext(createWizardContext(pair, getDisposable()));
    }
    JPanel component = pair.first;
    myPair = pair;
    UiNotifyConnector.doWhenFirstShown(myPair.second, () -> ScrollingUtil.ensureSelectionExists(myPair.second));

    ActionGroupPanelWrapper.installQuickSearch(pair.second);
    return component;
  }

  private static @NotNull WizardContext createWizardContext(@NotNull Pair<JPanel, JBList<AnAction>> pair, Disposable disposable) {
    WizardContext wizardContext = new WizardContext(null, disposable);
    wizardContext.addContextListener(new WizardContext.Listener() {
      @Override
      public void switchToRequested(@NotNull String placeId, @NotNull Consumer<? super Step> configure) {
        String wizardName = clearPrefix(placeId);
        List<AnAction> allWizards = SequencesKt.toList(UiUtils.asSequence(pair.second.getModel()));
        AnAction wizardToSelect = ContainerUtil.find(allWizards, wizard -> wizard.getTemplateText().equals(wizardName));
        if (wizardToSelect != null) {
          pair.second.setSelectedValue(wizardToSelect, true);
        }
        if (wizardToSelect instanceof ProjectSettingsStepBase<?> stepBase) {
          ProjectGeneratorPeer<?> peer = stepBase.getPeer();
          configure.accept(new ProjectStepPeerHolder(peer));
        }
      }

      private static @NotNull String clearPrefix(@NotNull String placeId) {
        return placeId.startsWith(NPW_PREFIX) ? placeId.substring(NPW_PREFIX.length()) : placeId;
      }
    });
    return wizardContext;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return FlatWelcomeFrame.getPreferredFocusedComponent(myPair);
  }

  @Override
  protected @Nullable JComponent createSouthPanel() {
    return null;
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  protected abstract DefaultActionGroup createRootStep();

  @Override
  protected String getHelpId() {
    return "create_new_project_dialog";
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[0];
  }

  static class ProjectStepPeerHolder extends StepAdapter {
    private final ProjectGeneratorPeer<?> myPeer;
    ProjectStepPeerHolder(ProjectGeneratorPeer<?> peer) {
      myPeer = peer;
    }

    ProjectGeneratorPeer<?> getPeer() {
      return myPeer;
    }

    @Override
    public JComponent getComponent() {
      return myPeer.getComponent();
    }
  }
}
