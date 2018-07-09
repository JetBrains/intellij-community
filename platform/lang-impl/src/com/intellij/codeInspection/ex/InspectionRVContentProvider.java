// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefDirectory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class InspectionRVContentProvider {
  private static final Logger LOG = Logger.getInstance(InspectionRVContentProvider.class);
  private final Project myProject;

  public InspectionRVContentProvider(@NotNull Project project) {
    myProject = project;
  }

  protected static class RefEntityContainer<Descriptor> {
    private final Descriptor[] myDescriptors;
    @Nullable
    private final RefEntity myEntity;

    public RefEntityContainer(@Nullable RefEntity entity, Descriptor[] descriptors) {
      myEntity = entity;
      myDescriptors = descriptors;
    }

    @Nullable
    public RefEntityContainer<Descriptor> getOwner() {
      if (myEntity == null) return null;
      final RefEntity entity = myEntity.getOwner();
      return entity instanceof RefElement && !(entity instanceof RefDirectory)
             ? new RefEntityContainer<>(entity, myDescriptors)
             : null;
    }

    @NotNull
    public RefElementNode createNode(@NotNull InspectionToolPresentation presentation) {
      return ReadAction.compute(() -> presentation.createRefNode(myEntity));
    }

    @Nullable
    public RefEntity getRefEntity() {
      return myEntity;
    }

    @Nullable
    public String getModule() {
      final RefModule refModule = myEntity instanceof RefElement
                                  ? ((RefElement)myEntity).getModule()
                                  : myEntity instanceof RefModule ? (RefModule)myEntity : null;
      return refModule != null ? refModule.getName() : null;
    }

    boolean areEqual(final @NotNull RefEntity o1, final @NotNull RefEntity o2) {
      return Comparing.equal(o1, o2);
    }

    boolean supportStructure() {
      return myEntity == null || myEntity instanceof RefElement && !(myEntity instanceof RefDirectory); //do not show structure for refModule and refPackage
    }

    public Descriptor[] getDescriptors() {
      return myDescriptors;
    }
  }

  public abstract boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context, @NotNull InspectionToolWrapper toolWrapper);

  public Iterable<? extends ScopeToolState> getTools(Tools tools) {
    return tools.getTools();
  }

  public boolean hasQuickFixes(InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths == null) return false;
    for (TreePath selectionPath : treePaths) {
      if (!TreeUtil.treeNodeTraverser((TreeNode)selectionPath.getLastPathComponent())
        .traverse(TreeTraversal.PRE_ORDER_DFS)
        .processEach(node -> {
        if (!((InspectionTreeNode) node).isValid()) return true;
        if (node instanceof ProblemDescriptionNode) {
          ProblemDescriptionNode problemDescriptionNode = (ProblemDescriptionNode)node;
          if (!problemDescriptionNode.isQuickFixAppliedFromView()) {
            final CommonProblemDescriptor descriptor = problemDescriptionNode.getDescriptor();
            final QuickFix[] fixes = descriptor != null ? descriptor.getFixes() : null;
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

  @NotNull
  public abstract QuickFixAction[] getCommonQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTree tree);

  @NotNull
  public QuickFixAction[] getPartialQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTree tree) {
    GlobalInspectionContextImpl context = tree.getContext();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    CommonProblemDescriptor[] descriptors = tree.getSelectedDescriptors();
    Map<String, FixAndOccurrences> result = new THashMap<>();
    for (CommonProblemDescriptor d : descriptors) {
      QuickFix[] fixes = d.getFixes();
      if (fixes == null || fixes.length == 0) continue;
      for (QuickFix fix : fixes) {
        String familyName = fix.getFamilyName();
        FixAndOccurrences fixAndOccurrences = result.get(familyName);
        if (fixAndOccurrences == null) {
          LocalQuickFixWrapper localQuickFixWrapper = new LocalQuickFixWrapper(fix, presentation.getToolWrapper());
          try {
            localQuickFixWrapper.setText(StringUtil.escapeMnemonics(fix.getFamilyName()));
          }
          catch (AbstractMethodError e) {
            //for plugin compatibility
            localQuickFixWrapper.setText("Name is not available");
          }
          fixAndOccurrences = new FixAndOccurrences(localQuickFixWrapper);
          result.put(familyName, fixAndOccurrences);
        } else {
          final LocalQuickFixWrapper quickFixAction = fixAndOccurrences.fix;
          checkFixClass(presentation, fix, quickFixAction);
        }
        fixAndOccurrences.occurrences++;
      }
    }

    return result
      .values()
      .stream()
      .filter(fixAndOccurrence -> fixAndOccurrence.occurrences != descriptors.length)
      .sorted(Comparator.comparingInt((FixAndOccurrences fixAndOccurrence) -> fixAndOccurrence.occurrences).reversed())
      .map(fixAndOccurrence -> {
        LocalQuickFixWrapper fix = fixAndOccurrence.fix;
        int occurrences = fixAndOccurrence.occurrences;
        fix.setText(fix.getText() + " (" + occurrences + " problem" + (occurrences == 1 ? "" : "s") + ")");
        return fix;
      })
      .toArray(QuickFixAction[]::new);
  }

  protected static void checkFixClass(InspectionToolPresentation presentation, QuickFix fix, LocalQuickFixWrapper quickFixAction) {
    Class class1 = getFixClass(fix);
    Class class2 = getFixClass(quickFixAction.getFix());
    if (!class1.equals(class2)) {
      String message = MessageFormat.format(
        "QuickFix-es with the same family name ({0}) should be the same class instances but actually are {1} and {2} instances. " +
        "Please assign reported exception for the inspection \"{3}\" (\"{4}\") developer.",
        fix.getFamilyName(), class1.getName(), class2.getName(), presentation.getToolWrapper().getTool().getClass(),
        presentation.getToolWrapper().getShortName());
      AssertionError error = new AssertionError(message);
      StreamEx.of(presentation.getProblemDescriptors()).select(ProblemDescriptorBase.class)
              .map(ProblemDescriptorBase::getCreationTrace).nonNull()
              .map(InspectionRVContentProvider::extractStackTrace).findFirst()
              .ifPresent(error::setStackTrace);
      LOG.error(message, error);
    }
  }

  private static StackTraceElement[] extractStackTrace(Throwable throwable) {
    // Remove top-of-stack frames which are common for different inspections,
    // leaving only inspection-specific frames
    Set<String> classes = StreamEx.of(ProblemDescriptorBase.class, InspectionManagerBase.class, ProblemsHolder.class)
      .map(Class::getName).toSet();
    return StreamEx.of(throwable.getStackTrace())
            .dropWhile(ste -> classes.contains(ste.getClassName()))
            .toArray(StackTraceElement.class);
  }

  public InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                              @NotNull InspectionNode toolNode,
                                              @NotNull InspectionTreeNode parentNode,
                                              boolean showStructure,
                                              boolean groupBySeverity) {
    InspectionToolWrapper wrapper = toolNode.getToolWrapper();
    InspectionToolPresentation presentation = context.getPresentation(wrapper);
    Map<String, Set<RefEntity>> content = presentation.getContent();
    return appendToolNodeContent(context, toolNode, parentNode, showStructure, groupBySeverity, content, entity -> {
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

  public abstract InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                                           @NotNull InspectionNode toolNode,
                                                           @NotNull InspectionTreeNode parentNode,
                                                           final boolean showStructure,
                                                           boolean groupBySeverity,
                                                           @NotNull Map<String, Set<RefEntity>> contents,
                                                           @NotNull Function<RefEntity, CommonProblemDescriptor[]> problems);

  protected abstract void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                           @NotNull InspectionToolWrapper toolWrapper,
                                           @NotNull RefEntityContainer container,
                                           @NotNull InspectionTreeNode pNode,
                                           final boolean canPackageRepeat);

  public boolean isContentLoaded() {
    return true;
  }

  protected <T> void buildTree(@NotNull GlobalInspectionContextImpl context,
                               @NotNull Map<String, Set<T>> packageContents,
                               final boolean canPackageRepeat,
                               @NotNull InspectionToolWrapper toolWrapper,
                               @NotNull Function<T, RefEntityContainer<?>> computeContainer,
                               final boolean showStructure,
                               final UnaryOperator<InspectionTreeNode> createdNodesConsumer) {
    final Map<String, Map<String, InspectionPackageNode>> module2PackageMap = new HashMap<>();
    boolean supportStructure = showStructure;
    final MultiMap<InspectionPackageNode, RefEntityContainer<?>> packageDescriptors = new MultiMap<>();
    for (String packageName : packageContents.keySet()) {
      final Set<T> elements = packageContents.get(packageName);
      for (T userObject : elements) {
        final RefEntityContainer container = computeContainer.apply(userObject);
        supportStructure &= container.supportStructure();
        final String moduleName = showStructure ? container.getModule() : null;
        Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        if (packageNodes == null) {
          packageNodes = new HashMap<>();
          module2PackageMap.put(moduleName, packageNodes);
        }
        InspectionPackageNode pNode = packageNodes.get(packageName);
        if (pNode == null) {
          pNode = new InspectionPackageNode(packageName);
          packageNodes.put(packageName, pNode);
        }

        packageDescriptors.putValue(pNode, container);
      }
    }

    if (supportStructure) {
      final HashMap<String, InspectionModuleNode> moduleNodes = new HashMap<>();
      for (final String moduleName : module2PackageMap.keySet()) {
        final Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        InspectionModuleNode moduleNode = moduleNodes.get(moduleName);

        if (moduleNode == null) {
          if (moduleName != null) {
            final Module module = ReadAction.compute(() -> ModuleManager.getInstance(myProject).findModuleByName(moduleName));
            if (module != null) {
              moduleNode = new InspectionModuleNode(module);
              moduleNodes.put(moduleName, moduleNode);
              moduleNode = (InspectionModuleNode)createdNodesConsumer.apply(moduleNode);
            }
            else { //module content was removed ?
              continue;
            }
          }
          else {
            for (InspectionPackageNode packageNode : packageNodes.values()) {
              for (RefEntityContainer<?> container : packageDescriptors.get(packageNode)) {
                appendDescriptor(context, toolWrapper, container, packageNode, canPackageRepeat);
              }
              createdNodesConsumer.apply(packageNode);
            }
            continue;
          }
        } else {
          moduleNode = (InspectionModuleNode)createdNodesConsumer.apply(moduleNode);
        }
        for (InspectionPackageNode packageNode : packageNodes.values()) {
          if (packageNode.getPackageName() != null) {
            Collection<RefEntityContainer<?>> objectContainers = packageDescriptors.get(packageNode);
            packageNode = (InspectionPackageNode)merge(packageNode, moduleNode, true);
            for (RefEntityContainer<?> container : objectContainers) {
              appendDescriptor(context, toolWrapper, container, packageNode, canPackageRepeat);
            }
          }
          else {
            for (RefEntityContainer<?> container : packageDescriptors.get(packageNode)) {
              appendDescriptor(context, toolWrapper, container, moduleNode, canPackageRepeat);
            }
          }
        }
      }
    }
    else {
      for (Map<String, InspectionPackageNode> packageNodes : module2PackageMap.values()) {
        for (InspectionPackageNode pNode : packageNodes.values()) {
          for (RefEntityContainer<?> container : packageDescriptors.get(pNode)) {
            appendDescriptor(context, toolWrapper, container, pNode, canPackageRepeat);
          }
          final int count = pNode.getChildCount();
          final ArrayList<TreeNode> childNodes = new ArrayList<>(count);
          for (int i = 0; i < count; i++) {
            childNodes.add(pNode.getChildAt(i));
          }
          for (TreeNode childNode: childNodes) {
            if (childNode instanceof ProblemDescriptionNode) {
              createdNodesConsumer.apply(pNode);
              break;
            }
            LOG.assertTrue(childNode instanceof RefElementNode, childNode.getClass().getName());
            final RefElementNode elementNode = (RefElementNode)childNode;
            final Set<RefElementNode> parentNodes = new LinkedHashSet<>();
            if (pNode.getPackageName() != null) {
              parentNodes.add(elementNode);
            } else {
              boolean hasElementNodeUnder = true;
              for(int e = 0; e < elementNode.getChildCount(); e++) {
                final TreeNode grandChildNode = elementNode.getChildAt(e);
                if (grandChildNode instanceof ProblemDescriptionNode) {
                  hasElementNodeUnder = false;
                  break;
                }
                LOG.assertTrue(grandChildNode instanceof RefElementNode);
                parentNodes.add((RefElementNode)grandChildNode);
              }
              if (!hasElementNodeUnder) {
                createdNodesConsumer.apply(elementNode);
                continue;
              }
            }

            //allow unused declaration to have structure at file level even when there are unused parameters
            if (!HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME.equals(toolWrapper.getShortName())) {
              for (RefElementNode parentNode : parentNodes) {
                final List<ProblemDescriptionNode> nodes = new ArrayList<>();
                TreeUtil.traverse(parentNode, node -> {
                  if (node instanceof ProblemDescriptionNode) {
                    nodes.add((ProblemDescriptionNode)node);
                  }
                  return true;
                });
                if (nodes.isEmpty()) continue;
                parentNode.removeAllChildren();
                for (ProblemDescriptionNode node : nodes) {
                  parentNode.insertByOrder(node, false);
                }
              }
            }
            for (RefElementNode node : parentNodes) {
              createdNodesConsumer.apply(node);
            }
          }
        }
      }
    }
  }

  @NotNull
  protected static RefElementNode addNodeToParent(@NotNull RefEntityContainer container,
                                                  @NotNull InspectionToolPresentation presentation,
                                                  final InspectionTreeNode parentNode) {
    final RefElementNode nodeToBeAdded = container.createNode(presentation);
    final Ref<Boolean> firstLevel = new Ref<>(true);
    RefElementNode prevNode = null;
    final Ref<RefElementNode> result = new Ref<>();
    while (true) {
      final RefElementNode currentNode = firstLevel.get() ? nodeToBeAdded : container.createNode(presentation);
      final RefEntityContainer finalContainer = container;
      final RefElementNode finalPrevNode = prevNode;
      TreeUtil.treeNodeTraverser(parentNode).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(node -> {
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          final RefEntity userObject = finalContainer.getRefEntity();
          final RefEntity object = refElementNode.getElement();
          if (userObject != null &&
              object != null &&
              (userObject.getClass().equals(object.getClass())) &&
              finalContainer.areEqual(object, userObject)) {
            if (firstLevel.get()) {
              result.set(refElementNode);
            }
            else {
              refElementNode.insertByOrder(finalPrevNode, false);
              result.set(nodeToBeAdded);
            }
            return false;
          }
        }
        return true;
      });
      if(!result.isNull()) return result.get();

      if (!firstLevel.get()) {
        currentNode.insertByOrder(prevNode, false);
      }
      final RefEntityContainer owner = container.getOwner();
      if (owner == null) {
        parentNode.insertByOrder(currentNode, false);
        return nodeToBeAdded;
      }
      container = owner;
      prevNode = currentNode;
      firstLevel.set(false);
    }
  }

  @SuppressWarnings({"ConstantConditions"}) //class cast suppression
  public static InspectionTreeNode merge(InspectionTreeNode child, InspectionTreeNode parent, boolean merge) {
    return ReadAction.compute(() -> {
      if (merge) {
        for (int i = 0; i < parent.getChildCount(); i++) {
          InspectionTreeNode current = (InspectionTreeNode)parent.getChildAt(i);
          if (child.getClass() != current.getClass()) {
            continue;
          }
          if (current instanceof InspectionPackageNode) {
            if (((InspectionPackageNode)current).getPackageName().compareTo(((InspectionPackageNode)child).getPackageName()) == 0) {
              processDepth(child, current);
              return current;
            }
          }
          else if (current instanceof RefElementNode) {
            if (InspectionResultsViewComparator.getInstance().compare(current, child) == 0) {
              processDepth(child, current);
              return current;
            }
          }
          else if (current instanceof InspectionNode) {
            if (((InspectionNode)current).getToolWrapper().getShortName().compareTo(((InspectionNode)child).getToolWrapper().getShortName()) == 0) {
              processDepth(child, current);
              return current;
            }
          }
          else if (current instanceof InspectionModuleNode) {
            if (((InspectionModuleNode)current).getName().compareTo(((InspectionModuleNode)child).getName()) == 0) {
              processDepth(child, current);
              return current;
            }
          }
        }
      }
      return parent.insertByOrder(child, false);
    });
  }

  private static void processDepth(final InspectionTreeNode child, final InspectionTreeNode current) {
    InspectionTreeNode[] children = new InspectionTreeNode[child.getChildCount()];
    for (int i = 0; i < children.length; i++) {
      children[i] = (InspectionTreeNode)child.getChildAt(i);
    }
    for (InspectionTreeNode node : children) {
      merge(node, current, true);
    }
  }

  @NotNull
  protected static QuickFixAction[] getCommonFixes(@NotNull InspectionToolPresentation presentation,
                                                   @NotNull CommonProblemDescriptor[] descriptors) {
    Map<String, LocalQuickFixWrapper> result = null;
    for (CommonProblemDescriptor d : descriptors) {
      QuickFix[] fixes = d.getFixes();
      if (fixes == null || fixes.length == 0) continue;
      if (result == null) {
        result = new HashMap<>();
        for (QuickFix fix : fixes) {
          if (fix == null) continue;
          result.put(fix.getFamilyName(), new LocalQuickFixWrapper(fix, presentation.getToolWrapper()));
        }
      }
      else {
        for (String familyName : new ArrayList<>(result.keySet())) {
          boolean isFound = false;
          for (QuickFix fix : fixes) {
            if (fix == null) continue;
            if (familyName.equals(fix.getFamilyName())) {
              isFound = true;
              final LocalQuickFixWrapper quickFixAction = result.get(fix.getFamilyName());
              checkFixClass(presentation, fix, quickFixAction);
              try {
                quickFixAction.setText(StringUtil.escapeMnemonics(fix.getFamilyName()));
              }
              catch (AbstractMethodError e) {
                //for plugin compatibility
                quickFixAction.setText("Name is not available");
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

  private static Class getFixClass(QuickFix fix) {
    return fix instanceof ActionClassHolder ? ((ActionClassHolder)fix).getActionClass() : fix.getClass();
  }

  private static class FixAndOccurrences {
    final LocalQuickFixWrapper fix;
    int occurrences;
    FixAndOccurrences(LocalQuickFixWrapper fix) {this.fix = fix;}
  }
}
