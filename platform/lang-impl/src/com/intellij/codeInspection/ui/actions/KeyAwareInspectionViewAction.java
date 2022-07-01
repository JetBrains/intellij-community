// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public abstract class KeyAwareInspectionViewAction extends InspectionViewActionBase {
  private static final Logger LOG = Logger.getInstance(KeyAwareInspectionViewAction.class);

  public KeyAwareInspectionViewAction(String name) {
    this(() -> name);
  }

  public KeyAwareInspectionViewAction(@NotNull Supplier<String> name) {
    super(name);
  }

  @Override
  protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
    InspectionToolWrapper<?, ?> wrapper = getToolWrapper(e);
    return wrapper != null && HighlightDisplayKey.find(wrapper.getShortName()) != null;
  }

  @Nullable
  protected static InspectionToolWrapper<?, ?> getToolWrapper(AnActionEvent e) {
    Object selectedNode = e.getData(PlatformCoreDataKeys.SELECTED_ITEM);
    InspectionToolWrapper<?, ?> wrapper = null;
    if (selectedNode instanceof InspectionGroupNode) {
      return null;
    }
    if (selectedNode instanceof InspectionNode) {
      wrapper = ((InspectionNode)selectedNode).getToolWrapper();
    }
    else if (selectedNode instanceof SuppressableInspectionTreeNode) {
      wrapper = ((SuppressableInspectionTreeNode)selectedNode).getPresentation().getToolWrapper();
    }
    if (wrapper != null) {
      return wrapper;
    }
    InspectionResultsView view = getView(e);
    if (view != null && view.isSingleInspectionRun()) {
      InspectionProfileImpl profile = view.getCurrentProfile();
      String singleToolName = profile.getSingleTool();
      if (singleToolName != null) {
        return profile.getInspectionTool(singleToolName, e.getProject());
      }
    }
    return null;
  }

  public static class DisableInspection extends KeyAwareInspectionViewAction {
    public DisableInspection() {
      super(DisableInspectionToolAction.getNameText());
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
      final InspectionToolWrapper<?, ?> wrapper = getToolWrapper(e);
      if (wrapper == null) {
        return false;
      }
      final HighlightDisplayKey key = HighlightDisplayKey.find(wrapper.getShortName());
      if (key == null) {
        return false;
      }
      return InspectionProjectProfileManager.getInstance(view.getProject()).getCurrentProfile().isToolEnabled(key);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InspectionResultsView view = getView(e);
      InspectionToolWrapper<?, ?> wrapper = getToolWrapper(e);
      String shortName = Objects.requireNonNull(wrapper).getShortName();
      if (Objects.requireNonNull(view).isSingleInspectionRun()) {
        view.getCurrentProfile().modifyProfile(it -> it.setToolEnabled(shortName, false));
      }
      else {
        final RefEntity[] selectedElements = view.getTree().getSelectedElements();
        final Set<PsiElement> files = new HashSet<>();
        for (RefEntity selectedElement : selectedElements) {
          if (selectedElement instanceof RefElement) {
            files.add(((RefElement)selectedElement).getPsiElement());
          }
        }

        if (files.isEmpty()) {
          view.getCurrentProfile().modifyProfile(it -> it.setToolEnabled(shortName, false));
        }
        else {
          InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(view.getProject(), it -> {
            for (PsiElement element : files) {
              it.disableTool(shortName, element);
            }
          });
        }
      }
    }
  }

  public static class RunInspectionOn extends KeyAwareInspectionViewAction {
    public RunInspectionOn() {
      super(InspectionsBundle.messagePointer("run.inspection.on.file.intention.text"));
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
      return super.isEnabled(view, e) && getSelectedElements(e) != null;
    }

    @Nullable
    private static PsiElement getSelectedElements(AnActionEvent e) {
      PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
      if (element != null) {
        return element;
      }
      Object node = e.getData(PlatformCoreDataKeys.SELECTED_ITEM);
      if (node instanceof InspectionTreeNode) {
        HashSet<RefEntity> entities = new HashSet<>();
        InspectionTree.addElementsInNode((InspectionTreeNode)node, entities);
        RefEntity refEntity = ContainerUtil.find(entities, entity -> entity instanceof RefElement);
        return refEntity != null ? ((RefElement)refEntity).getPsiElement() : null;
      }
      return null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InspectionResultsView view = getView(e);
      Set<PsiFile> files = new HashSet<>();
      for (RefEntity entity : Objects.requireNonNull(view).getTree().getSelectedElements()) {
        if (entity instanceof RefElement && entity.isValid()) {
          final PsiElement element = ((RefElement)entity).getPsiElement();
          final PsiFile file = element.getContainingFile();
          files.add(file);
        }
      }

      boolean useModule = true;
      Module module = null;
      for (PsiFile file : files) {
        final Module currentFileModule = ModuleUtilCore.findModuleForPsiElement(file);
        if (currentFileModule != null) {
          if (module == null) {
            module = currentFileModule;
          }
          else if (currentFileModule != module) {
            useModule = false;
            break;
          }
        }
        else {
          useModule = false;
          break;
        }
      }

      final PsiElement context;
      final AnalysisScope scope;
      switch (files.size()) {
        case 0:
          context = null;
          scope = view.getScope();
          break;
        case 1:
          final PsiFile theFile = ContainerUtil.getFirstItem(files);
          LOG.assertTrue(theFile != null);
          context = theFile;
          scope = new AnalysisScope(theFile);
          break;
        default:
          context = null;
          scope = new AnalysisScope(view.getProject(), ContainerUtil.map(files, PsiFile::getVirtualFile));
      }

      RunInspectionIntention.selectScopeAndRunInspection(Objects.requireNonNull(getToolWrapper(e)).getShortName(), scope, useModule ? module : null, context, view.getProject());
    }
  }
}
