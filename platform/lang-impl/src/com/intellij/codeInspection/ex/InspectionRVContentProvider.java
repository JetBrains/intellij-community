// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefDirectory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.*;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.tree.TreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Function;

public abstract class InspectionRVContentProvider {
  public InspectionRVContentProvider() {
  }

  protected static class RefEntityContainer<Descriptor> {
    private final Descriptor[] myDescriptors;
    private final @Nullable RefEntity myEntity;

    public RefEntityContainer(@Nullable RefEntity entity, Descriptor[] descriptors) {
      myEntity = entity;
      myDescriptors = descriptors;
    }

    public @NotNull RefElementNode createNode(@NotNull InspectionToolPresentation presentation,
                                              InspectionTreeModel model,
                                              InspectionTreeNode topParent,
                                              boolean showStructure) {
      RefEntityContainer<Descriptor> owner = getOwner();
      InspectionTreeNode parent;
      if (owner == null) {
        parent = topParent;
      } else {
        parent = owner.createNode(presentation, model, topParent, showStructure);
        if (!showStructure) {
          return (RefElementNode)parent;
        }
      }
      return model.createRefElementNode(myEntity, () -> presentation.createRefNode(myEntity, model, parent), parent);
    }

    public @Nullable RefEntity getRefEntity() {
      return myEntity;
    }

    protected @Nullable String getModuleName() {
      final RefModule refModule = myEntity instanceof RefElement
                                  ? ((RefElement)myEntity).getModule()
                                  : myEntity instanceof RefModule ? (RefModule)myEntity : null;
      return refModule != null ? refModule.getName() : null;
    }

    public @Nullable Module getModule(Project project) {
      String name = getModuleName();
      if (name == null) return null;
      return ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(name));
    }

    boolean supportStructure() {
      return myEntity == null || myEntity instanceof RefElement && !(myEntity instanceof RefDirectory); //do not show structure for refModule and refPackage
    }

    public Descriptor[] getDescriptors() {
      return myDescriptors;
    }

    private @Nullable RefEntityContainer<Descriptor> getOwner() {
      if (myEntity == null) return null;
      final RefEntity entity = myEntity.getOwner();
      return entity instanceof RefElement && !(entity instanceof RefDirectory)
             ? new RefEntityContainer<>(entity, myDescriptors)
             : null;
    }
  }

  public abstract boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context, @NotNull InspectionToolWrapper toolWrapper);

  public Iterable<? extends ScopeToolState> getTools(Tools tools) {
    return tools.getTools();
  }

  public boolean hasQuickFixes(@NotNull AnActionEvent e) {
    final Object[] selection = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (selection == null) return false;
    for (Object selectedNode : selection) {
      if (!TreeUtil.treeNodeTraverser((TreeNode)selectedNode)
        .traverse(TreeTraversal.PRE_ORDER_DFS)
        .processEach(node -> {
        if (!((InspectionTreeNode) node).isValid()) return true;
        if (node instanceof ProblemDescriptionNode problemDescriptionNode) {
          if (!problemDescriptionNode.isQuickFixAppliedFromView()) {
            final CommonProblemDescriptor descriptor = problemDescriptionNode.getDescriptor();
            final QuickFix<?>[] fixes = descriptor != null ? descriptor.getFixes() : null;
            return fixes == null || fixes.length == 0;
          }
        }
        return true;
      })) {
        return true;
      }
    }
    return false;
  }

  public abstract QuickFixAction @NotNull [] getCommonQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTree tree,
                                                                 CommonProblemDescriptor @NotNull [] descriptors,
                                                                 RefEntity @NotNull [] refElements);

  public QuickFixAction @NotNull [] getPartialQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTree tree,
                                                         CommonProblemDescriptor @NotNull [] selectedDescriptors) {
    GlobalInspectionContextImpl context = tree.getContext();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    Map<String, FixAndOccurrences> result = new LinkedHashMap<>();
    for (CommonProblemDescriptor d : selectedDescriptors) {
      QuickFix<?>[] fixes = d.getFixes();
      if (fixes == null) continue;
      for (QuickFix<?> fix : fixes) {
        String familyName = fix.getFamilyName();
        FixAndOccurrences fixAndOccurrences = result.get(familyName);
        if (fixAndOccurrences == null) {
          LocalQuickFixWrapper localQuickFixWrapper = new LocalQuickFixWrapper(fix, presentation.getToolWrapper());
          try {
            localQuickFixWrapper.setText(StringUtil.escapeMnemonics(fix.getFamilyName()));
          }
          catch (AbstractMethodError e) {
            //for plugin compatibility
            localQuickFixWrapper.setText(LangBundle.message("action.name.not.available.text"));
          }
          fixAndOccurrences = new FixAndOccurrences(localQuickFixWrapper);
          result.put(familyName, fixAndOccurrences);
        } else {
          final QuickFixAction quickFixAction = fixAndOccurrences.fix;
          if (quickFixAction instanceof LocalQuickFixesWrapper) {
            ((LocalQuickFixesWrapper)quickFixAction).addFixAction(fix, presentation.getToolWrapper());
          } else {
            assert quickFixAction instanceof LocalQuickFixWrapper;
            if (fix.getClass() != ((LocalQuickFixWrapper)quickFixAction).getFix().getClass()) {
              fixAndOccurrences.fix = new LocalQuickFixesWrapper(quickFixAction.getText(),
                                                                 List.of(((LocalQuickFixWrapper)quickFixAction).getFix(), fix),
                                                                 presentation.getToolWrapper());
            }
          }
        }
        fixAndOccurrences.occurrences++;
      }
    }

    return result
      .values()
      .stream()
      .filter(fixAndOccurrence -> fixAndOccurrence.occurrences != selectedDescriptors.length)
      .sorted(Comparator.comparingInt((FixAndOccurrences fixAndOccurrence) -> fixAndOccurrence.occurrences).reversed())
      .map(fixAndOccurrence -> {
        QuickFixAction fix = fixAndOccurrence.fix;
        int occurrences = fixAndOccurrence.occurrences;
        if (fix instanceof LocalQuickFixWrapper) {
          ((LocalQuickFixWrapper)fix).setText(LangBundle.message("action.fix.n.problems.text", fix.getText(), occurrences));
        } else if (fix instanceof LocalQuickFixesWrapper) {
          ((LocalQuickFixesWrapper)fix).setText(LangBundle.message("action.fix.n.problems.text", fix.getText(), occurrences));
        }
        return fix;
      })
      .toArray(QuickFixAction[]::new);
  }

  private static StackTraceElement[] extractStackTrace(Throwable throwable) {
    // Remove top-of-stack frames which are common for different inspections,
    // leaving only inspection-specific frames
    Set<String> classes = ContainerUtil.newHashSet(ProblemDescriptorBase.class.getName(), InspectionManagerBase.class.getName(), ProblemsHolder.class.getName());

    return StreamEx.of(throwable.getStackTrace())
            .dropWhile(ste -> classes.contains(ste.getClassName()))
            .toArray(StackTraceElement.class);
  }

  public void appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                    @NotNull InspectionToolWrapper wrapper,
                                    @NotNull InspectionTreeNode parentNode,
                                    boolean showStructure,
                                    boolean groupBySeverity) {
    InspectionToolPresentation presentation = context.getPresentation(wrapper);
    Map<String, Set<RefEntity>> content = presentation.getContent();
    appendToolNodeContent(context, wrapper, parentNode, showStructure, groupBySeverity, content, entity -> {
      if (context.getUIOptions().FILTER_RESOLVED_ITEMS) {
        return presentation.isExcluded(entity) ? null : presentation.getProblemElements().get(entity);
      } else {
        CommonProblemDescriptor[] problems = ObjectUtils.notNull(presentation.getProblemElements().get(entity), CommonProblemDescriptor.EMPTY_ARRAY);
        CommonProblemDescriptor[] suppressedProblems = presentation.getSuppressedProblems(entity);
        CommonProblemDescriptor[] resolvedProblems = presentation.getResolvedProblems(entity);
        CommonProblemDescriptor[] result = new CommonProblemDescriptor[problems.length + suppressedProblems.length + resolvedProblems.length];
        System.arraycopy(problems, 0, result, 0, problems.length);
        System.arraycopy(suppressedProblems, 0, result, problems.length, suppressedProblems.length);
        System.arraycopy(resolvedProblems, 0, result, problems.length + suppressedProblems.length, resolvedProblems.length);
        return result;
      }
    });
  }

  public abstract void appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                             @NotNull InspectionToolWrapper wrapper,
                                             @NotNull InspectionTreeNode parentNode,
                                             final boolean showStructure,
                                             boolean groupBySeverity,
                                             @NotNull Map<String, Set<RefEntity>> contents,
                                             @NotNull Function<? super RefEntity, CommonProblemDescriptor[]> problems);

  protected abstract void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                           @NotNull InspectionToolWrapper toolWrapper,
                                           @NotNull RefEntityContainer container,
                                           @NotNull InspectionTreeNode parent);

  public boolean isContentLoaded() {
    return true;
  }

  protected <T> void buildTree(@NotNull GlobalInspectionContextImpl context,
                               @NotNull Map<String, Set<T>> packageContents,
                               @NotNull InspectionToolWrapper toolWrapper,
                               @NotNull Function<? super T, ? extends RefEntityContainer<?>> computeContainer,
                               boolean showStructure,
                               final InspectionTreeNode parent,
                               InspectionTreeModel model) {
    MultiMap<String, RefEntityContainer> evaluatedDescriptors = MultiMap.create();
    for (Map.Entry<String, Set<T>> entry : packageContents.entrySet()) {
      String packageName = entry.getKey();
      for (T problemDescriptor : entry.getValue()) {
        RefEntityContainer<?> container = computeContainer.apply(problemDescriptor);
        evaluatedDescriptors.putValue(packageName, container);
        showStructure &= container.supportStructure();
      }
    }

    for (Map.Entry<String, Collection<RefEntityContainer>> entry : evaluatedDescriptors.entrySet()) {
      for (RefEntityContainer container : entry.getValue()) {
        InspectionTreeNode currentParent = parent;
        if (showStructure) {
          String packageName = entry.getKey();
          Module module = container.getModule(context.getProject());
          InspectionTreeNode moduleNode = module != null ? model.createModuleNode(module, parent) : null;
          InspectionTreeNode actualParent = moduleNode == null ? parent : moduleNode;
          currentParent = packageName == null ? actualParent : model.createPackageNode(packageName, actualParent);
        }
        InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
        RefElementNode node = container.createNode(presentation,
                                                   model,
                                                   currentParent,
                                                   showStructure
                                                   || HighlightInfoType.getUnusedSymbolDisplayName().equals(toolWrapper.getDisplayName())
                                                   || presentation.isDummy());
        appendDescriptor(context, toolWrapper, container, node);
      }
    }
  }

  protected static QuickFixAction @NotNull [] getCommonFixes(@NotNull InspectionToolPresentation presentation,
                                                             CommonProblemDescriptor @NotNull [] descriptors) {
    Map<String, QuickFixAction> result = null;
    for (CommonProblemDescriptor d : descriptors) {
      QuickFix<?>[] fixes = d.getFixes();
      if (fixes == null || fixes.length == 0) continue;
      // Add LocalQuickFixWrapper-s for the first descriptor fixes
      if (result == null) {
        result = new LinkedHashMap<>();
        for (QuickFix<?> fix : fixes) {
          if (fix == null) continue;
          result.put(fix.getFamilyName(), new LocalQuickFixWrapper(fix, presentation.getToolWrapper()));
        }
      }
      // Remove non-shared fixes
      else {
        for (String familyName : new ArrayList<>(result.keySet())) {
          boolean isFound = false;
          for (QuickFix<?> fix : fixes) {
            if (fix == null) continue;
            if (familyName.equals(fix.getFamilyName())) {
              // Fixes of different classes with the same family name are joined in LocalQuickFixesWrapper
              isFound = true;
              final QuickFixAction quickFixAction = result.get(fix.getFamilyName());
              if (quickFixAction instanceof LocalQuickFixesWrapper) {
                ((LocalQuickFixesWrapper)quickFixAction).addFixAction(fix, presentation.getToolWrapper());
              } else {
                assert quickFixAction instanceof LocalQuickFixWrapper;
                result.remove(fix.getFamilyName());

                @NlsActions.ActionText String fixActionText;
                try {
                  fixActionText = fix.getFamilyName();
                }
                catch (AbstractMethodError e) {
                  fixActionText = LangBundle.message("action.name.not.available.text");
                }
                final var commonWrapper = new LocalQuickFixesWrapper(fixActionText,
                                                                     List.of(((LocalQuickFixWrapper)quickFixAction).getFix(), fix),
                                                                     presentation.getToolWrapper());
                result.put(fix.getFamilyName(), commonWrapper);
              }
              break;
            }
          }
          if (!isFound) {
            result.remove(familyName);
            if (result.isEmpty()) {
              return QuickFixAction.EMPTY;
            }
          }
        }
      }
    }
    return result == null || result.isEmpty() ? QuickFixAction.EMPTY : result.values().toArray(QuickFixAction.EMPTY);
  }

  private static final class FixAndOccurrences {
    QuickFixAction fix;
    int occurrences;
    FixAndOccurrences(QuickFixAction fix) {this.fix = fix;}
  }
}
