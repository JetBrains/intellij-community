// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CompositeCodeStyleSource implements CodeStyleSource {
  private final List<CodeStyleSource> mySourceList = ContainerUtil.newArrayList();

  public void addSource(@NotNull CodeStyleSource source) {
    mySourceList.add(source);
  }

  @Nullable
  @Override
  public CodeStyleStatusUIContributor getStatusUIContributor(@NotNull TransientCodeStyleSettings transientSettings) {
    return new MyUIContributor(transientSettings);
  }

  private class MyUIContributor implements CodeStyleStatusUIContributor {
    private final List<CodeStyleStatusUIContributor> myContributors = ContainerUtil.newArrayList();

    private MyUIContributor(@NotNull TransientCodeStyleSettings transientSettings) {
      for (CodeStyleSource source : mySourceList) {
        CodeStyleStatusUIContributor uiContributor = source.getStatusUIContributor(transientSettings);
        if (uiContributor != null) {
          myContributors.add(uiContributor);
        }
      }
    }

    @Override
    public boolean areActionsAvailable(@NotNull VirtualFile file) {
      for (CodeStyleStatusUIContributor uiContributor : myContributors) {
        if (uiContributor.areActionsAvailable(file)) return true;
      }
      return false;
    }

    @Override
    public AnAction[] getActions(@NotNull PsiFile file) {
      List<AnAction> actions = ContainerUtil.newArrayList();
      for (CodeStyleStatusUIContributor uiContributor : myContributors) {
        if (uiContributor.areActionsAvailable(file.getVirtualFile())) {
          final AnAction[] contributorActions = uiContributor.getActions(file);
          if (contributorActions != null) {
            actions.addAll(Arrays.asList(contributorActions));
          }
        }
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public String getTooltip() {
      if (myContributors.size() == 1) {
        return myContributors.get(0).getTooltip();
      }
      return "The current code style diverges from the project code style.";
    }

    @Nullable
    @Override
    public String getHint() {
      switch (myContributors.size()) {
        case 0:
          return null;
        case 1:
          return myContributors.get(0).getHint();
        default:
          return "Multiple Code Style Sources";
      }
    }

    @Nullable
    @Override
    public String getAdvertisementText(@NotNull PsiFile psiFile) {
      if (myContributors.size() == 1) {
        return myContributors.get(0).getAdvertisementText(psiFile);
      }
      return null;
    }

    @Nullable
    @Override
    public AnAction createDisableAction(@NotNull Project project) {
      if (!myContributors.isEmpty()) {
        return new ActionGroup() {
          @NotNull
          @Override
          public AnAction[] getChildren(@Nullable AnActionEvent e) {
            List<AnAction> disableActions = ContainerUtil.newArrayList();
            for (CodeStyleStatusUIContributor uiContributor : myContributors) {
              AnAction disableAction = uiContributor.createDisableAction(project);
              if (disableAction != null) {
                disableActions.add(disableAction);
              }
            }
            return disableActions.toArray(AnAction.EMPTY_ARRAY);
          }
        };
      }
      return null;
    }

  }
}
