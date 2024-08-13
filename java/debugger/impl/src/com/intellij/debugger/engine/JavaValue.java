// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.JavaReferringObjectsValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.debugger.memory.agent.MemoryAgentPathsToClosestGCRootsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.*;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.pinned.items.PinToTopMemberValue;
import com.intellij.xdebugger.impl.pinned.items.PinToTopParentValue;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedTextPopup;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.List;
import java.util.Set;

public class JavaValue extends XNamedValue implements NodeDescriptorProvider, XValueTextProvider,
                                                      PinToTopParentValue, PinToTopMemberValue {
  private static final Logger LOG = Logger.getInstance(JavaValue.class);

  private final boolean myCanBePinned;
  private final JavaValue myParent;
  @NotNull
  private final ValueDescriptorImpl myValueDescriptor;
  @NotNull
  private final EvaluationContextImpl myEvaluationContext;
  private final NodeManagerImpl myNodeManager;
  private final boolean myContextSet;

  protected JavaValue(JavaValue parent,
                      @NotNull ValueDescriptorImpl valueDescriptor,
                      @NotNull EvaluationContextImpl evaluationContext,
                      NodeManagerImpl nodeManager,
                      boolean contextSet) {
    this(parent, valueDescriptor.calcValueName(), valueDescriptor, evaluationContext, nodeManager, contextSet);
  }

  protected JavaValue(JavaValue parent,
                      String name,
                      @NotNull ValueDescriptorImpl valueDescriptor,
                      @NotNull EvaluationContextImpl evaluationContext,
                      NodeManagerImpl nodeManager,
                      boolean contextSet) {
    super(name);
    myParent = parent;
    myValueDescriptor = valueDescriptor;
    myEvaluationContext = evaluationContext;
    myNodeManager = nodeManager;
    myContextSet = contextSet;
    myCanBePinned = doComputeCanBePinned();
  }

  @Nullable
  @Override
  public String getTag() {
    Type type = myValueDescriptor.getType();
    return type == null ? null : type.name();
  }

  @Override
  public boolean canBePinned() {
    return myCanBePinned;
  }

  private boolean doComputeCanBePinned() {
    if (myValueDescriptor instanceof ArrayElementDescriptor) {
      return false;
    }
    return myParent != null;
  }

  public static JavaValue create(JavaValue parent,
                                 @NotNull ValueDescriptorImpl valueDescriptor,
                                 @NotNull EvaluationContextImpl evaluationContext,
                                 NodeManagerImpl nodeManager,
                                 boolean contextSet) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return new JavaValue(parent, valueDescriptor, evaluationContext, nodeManager, contextSet);
  }

  static JavaValue create(@NotNull ValueDescriptorImpl valueDescriptor,
                          @NotNull EvaluationContextImpl evaluationContext,
                          NodeManagerImpl nodeManager) {
    return create(null, valueDescriptor, evaluationContext, nodeManager, false);
  }

  public JavaValue getParent() {
    return myParent;
  }

  @Override
  @NotNull
  public ValueDescriptorImpl getDescriptor() {
    return myValueDescriptor;
  }

  @NotNull
  public EvaluationContextImpl getEvaluationContext() {
    return myEvaluationContext;
  }

  public NodeManagerImpl getNodeManager() {
    return myNodeManager;
  }

  private boolean isOnDemand() {
    return OnDemandRenderer.ON_DEMAND_CALCULATED.isIn(myValueDescriptor);
  }

  private boolean isCalculated() {
    return OnDemandRenderer.isCalculated(myValueDescriptor);
  }

  @Override
  public void computePresentation(@NotNull final XValueNode node, @NotNull XValuePlace place) {
    if (isOnDemand() && !isCalculated()) {
      myValueDescriptor.applyOnDemandPresentation(node);
      return;
    }
    myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      protected void commandCancelled() {
        node.setPresentation(null, new XErrorValuePresentation(JavaDebuggerBundle.message("error.context.has.changed")), false);
        cancelInitFuture();
      }

      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        if (node.isObsolete()) {
          cancelInitFuture();
          return;
        }
        if (!myContextSet) {
          myValueDescriptor.setContext(myEvaluationContext);
        }
        myValueDescriptor.updateRepresentationNoNotify(myEvaluationContext, new DescriptorLabelListener() {
          @Override
          public void labelChanged() {
            Icon nodeIcon = place == XValuePlace.TOOLTIP
                            ? myValueDescriptor.getValueIcon()
                            : DebuggerTreeRenderer.getValueIcon(myValueDescriptor, myParent != null ? myParent.getDescriptor() : null);

            XValuePresentation presentation = createPresentation(myValueDescriptor);
            Renderer lastRenderer = myValueDescriptor.getLastRenderer();
            boolean fullEvaluatorSet = setFullValueEvaluator(lastRenderer);
            if (!fullEvaluatorSet && lastRenderer instanceof CompoundReferenceRenderer) {
              fullEvaluatorSet = setFullValueEvaluator(((CompoundReferenceRenderer)lastRenderer).getLabelRenderer());
            }
            if (!fullEvaluatorSet) {
              String text = myValueDescriptor.getValueText();
              if (text.length() > XValueNode.MAX_VALUE_LENGTH) {
                node.setFullValueEvaluator(new JavaFullValueEvaluator(myEvaluationContext) {
                  @Override
                  public void evaluate(@NotNull final XFullValueEvaluationCallback callback) {
                    final ValueDescriptorImpl fullValueDescriptor = myValueDescriptor.getFullValueDescriptor();
                    fullValueDescriptor.updateRepresentation(myEvaluationContext, new DescriptorLabelListener() {
                      @Override
                      public void labelChanged() {
                        callback.evaluated(fullValueDescriptor.getValueText());
                      }
                    });
                  }
                });
              }
              else if (VisualizedTextPopup.INSTANCE.isVisualizable(text)) {
                node.setFullValueEvaluator(new XFullValueEvaluator() {
                  @Override
                  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
                    callback.evaluated(text);
                  }
                });
              }
            }
            node.setPresentation(nodeIcon, presentation, myValueDescriptor.isExpandable());
          }

          private boolean setFullValueEvaluator(Renderer renderer) {
            if (renderer instanceof FullValueEvaluatorProvider) {
              XFullValueEvaluator evaluator = ((FullValueEvaluatorProvider)renderer).getFullValueEvaluator(myEvaluationContext, myValueDescriptor);
              if (evaluator != null) {
                node.setFullValueEvaluator(evaluator);
                return true;
              }
            }
            return false;
          }
        });
      }

      private void cancelInitFuture() {
        var future = myValueDescriptor.getInitFuture();
        if (!future.isDone()) {
          future.cancel(false);
        }
      }
    });
  }

  public static XValuePresentation createPresentation(ValueDescriptorImpl descriptor) {
    Renderer lastLabelRenderer = descriptor.getLastLabelRenderer();
    if (lastLabelRenderer instanceof XValuePresentationProvider) {
      return ((XValuePresentationProvider)lastLabelRenderer).getPresentation(descriptor);
    }
    return new JavaValuePresentation(descriptor);
  }

  public abstract static class JavaFullValueEvaluator extends XFullValueEvaluator {
    protected final EvaluationContextImpl myEvaluationContext;

    public JavaFullValueEvaluator(@NotNull @Nls String linkText, EvaluationContextImpl evaluationContext) {
      super(linkText);
      myEvaluationContext = evaluationContext;
    }

    public JavaFullValueEvaluator(EvaluationContextImpl evaluationContext) {
      myEvaluationContext = evaluationContext;
    }

    public abstract void evaluate(@NotNull XFullValueEvaluationCallback callback) throws Exception;

    protected EvaluationContextImpl getEvaluationContext() {
      return myEvaluationContext;
    }

    @Override
    public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
      if (callback.isObsolete()) return;
      myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        protected void commandCancelled() {
          callback.errorOccurred(JavaDebuggerBundle.message("error.context.has.changed"));
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          if (callback.isObsolete()) return;
          evaluate(callback);
        }
      });
    }
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    computeChildren(-1, node);
  }

  private void computeChildren(int remainingElements, @NotNull final XCompositeNode node) {
    myValueDescriptor.getInitFuture().thenRun(() -> {
      scheduleCommand(myEvaluationContext, node, new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          myValueDescriptor.getChildrenRenderer(myEvaluationContext.getDebugProcess())
            .thenAccept(r -> {
              r.buildChildren(myValueDescriptor.getValue(), new MyChildrenBuilder(remainingElements, node), myEvaluationContext);
            });
        }
      });
    });
  }

  private class MyChildrenBuilder implements ChildrenBuilder {
    private final int remainingElements;
    private final @NotNull XCompositeNode node;

    MyChildrenBuilder(int remainingElements, @NotNull XCompositeNode node) {
      this.remainingElements = remainingElements;
      this.node = node;
    }

    @Override
    public NodeDescriptorFactory getDescriptorManager() {
      return myNodeManager;
    }

    @Override
    public NodeManager getNodeManager() {
      return myNodeManager;
    }

    @Override
    public ValueDescriptor getParentDescriptor() {
      return myValueDescriptor;
    }

    @Override
    public void initChildrenArrayRenderer(ArrayRenderer renderer, int arrayLength) {
      renderer.START_INDEX = 0;
      if (remainingElements >= 0) {
        renderer.START_INDEX = Math.max(0, arrayLength - remainingElements);
      }
    }

    @Override
    public void addChildren(List<? extends DebuggerTreeNode> nodes, boolean last) {
      XValueChildrenList childrenList = XValueChildrenList.EMPTY;
      if (!nodes.isEmpty()) {
        childrenList = new XValueChildrenList(nodes.size());
        for (DebuggerTreeNode treeNode : nodes) {
          NodeDescriptor descriptor = treeNode.getDescriptor();
          if (descriptor instanceof ValueDescriptorImpl) {
            // Value is calculated already in NodeManagerImpl
            childrenList.add(create(JavaValue.this, (ValueDescriptorImpl)descriptor, myEvaluationContext, myNodeManager, false));
          }
          else if (descriptor instanceof MessageDescriptor) {
            childrenList.add(
              new JavaStackFrame.DummyMessageValueNode(descriptor.getLabel(),
                                                       DebuggerTreeRenderer.getDescriptorIcon(descriptor)));
          }
        }
      }
      node.addChildren(childrenList, last);
    }

    @Override
    public void setChildren(List<? extends DebuggerTreeNode> nodes) {
      addChildren(nodes, true);
    }

    @Override
    public void setMessage(@NotNull String message,
                           @Nullable Icon icon,
                           @NotNull SimpleTextAttributes attributes,
                           @Nullable XDebuggerTreeNodeHyperlink link) {
      node.setMessage(message, icon, attributes, link);
    }

    @Override
    public void addChildren(@NotNull XValueChildrenList children, boolean last) {
      node.addChildren(children, last);
    }

    @Override
    public void tooManyChildren(int remaining) {
      node.tooManyChildren(remaining, () -> computeChildren(remaining, node));
    }

    @Override
    public void tooManyChildren(int remaining, @NotNull Runnable addNextChildren) {
      node.tooManyChildren(remaining, addNextChildren);
    }

    @Override
    public void setAlreadySorted(boolean alreadySorted) {
      node.setAlreadySorted(alreadySorted);
    }

    @Override
    public void setErrorMessage(@NotNull String errorMessage) {
      node.setErrorMessage(errorMessage);
    }

    @Override
    public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
      node.setErrorMessage(errorMessage, link);
    }

    @Override
    public boolean isObsolete() {
      return node.isObsolete();
    }
  }

  protected static boolean scheduleCommand(EvaluationContextImpl evaluationContext,
                                           @NotNull final XCompositeNode node,
                                           final SuspendContextCommandImpl command) {
    if (node.isObsolete()) {
      return false;
    }
    evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(command.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
        if (node.isObsolete()) {
          return;
        }
        command.contextAction(suspendContext);
      }

      @Override
      protected void commandCancelled() {
        node.setErrorMessage(JavaDebuggerBundle.message("error.context.has.changed"));
      }
    });
    return true;
  }

  @Override
  public void computeSourcePosition(@NotNull final XNavigatable navigatable) {
    computeSourcePosition(navigatable, false);
  }

  private void computeSourcePosition(@NotNull final XNavigatable navigatable, final boolean inline) {
    myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public Priority getPriority() {
        return inline ? Priority.LOWEST : Priority.NORMAL;
      }

      @Override
      protected void commandCancelled() {
        navigatable.setSourcePosition(null);
      }

      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        ReadAction.nonBlocking(() -> {
          SourcePosition position = SourcePositionProvider.getSourcePosition(myValueDescriptor, getProject(), getDebuggerContext(), false);
          if (position != null) {
            navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position));
          }
          if (inline) {
            position = SourcePositionProvider.getSourcePosition(myValueDescriptor, getProject(), getDebuggerContext(), true);
            if (position != null) {
              navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position));
            }
          }
        }).executeSynchronously();
      }
    });
  }

  @NotNull
  @Override
  public ThreeState computeInlineDebuggerData(@NotNull final XInlineDebuggerDataCallback callback) {
    // show fields only for 'this' node
    if (myValueDescriptor instanceof FieldDescriptor && myParent != null && !(myParent.myValueDescriptor instanceof ThisDescriptorImpl)) {
      return ThreeState.NO;
    }
    computeSourcePosition(callback::computed, true);
    return ThreeState.YES;
  }

  private DebuggerContextImpl getDebuggerContext() {
    return myEvaluationContext.getDebugProcess().getDebuggerContext();
  }

  public Project getProject() {
    return myValueDescriptor.getProject();
  }

  @Override
  public boolean canNavigateToTypeSource() {
    return true;
  }

  @Override
  public void computeTypeSourcePosition(@NotNull final XNavigatable navigatable) {
    if (myEvaluationContext.getSuspendContext().isResumed()) return;
    DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
    debugProcess.getManagerThread().schedule(new NavigateCommand(getDebuggerContext(), myValueDescriptor, debugProcess, null) {
      @Override
      public Priority getPriority() {
        return Priority.HIGH;
      }

      @Override
      protected void doAction(@Nullable final SourcePosition sourcePosition) {
        if (sourcePosition != null) {
          ReadAction.nonBlocking(() -> navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(sourcePosition)))
            .executeSynchronously();
        }
      }
    });
  }

  @Nullable
  @Override
  public XValueModifier getModifier() {
    return myValueDescriptor.canSetValue() ? myValueDescriptor.getModifier(this) : null;
  }

  private volatile XExpression evaluationExpression = null;

  @NotNull
  @Override
  public Promise<XExpression> calculateEvaluationExpression() {
    if (evaluationExpression != null) {
      return Promises.resolvedPromise(evaluationExpression);
    }
    else {
      final AsyncPromise<XExpression> result = new AsyncPromise<>();
      myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.HIGH;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          try {
            getDescriptor().getTreeEvaluation(JavaValue.this, getDebuggerContext())
              .whenComplete((psiExpression, ex) -> {
                if (ex != null) {
                  result.setError(ex);
                }
                else if (psiExpression != null) {
                  ReadAction.nonBlocking(() -> {
                    XExpression res = TextWithImportsImpl.toXExpression(new TextWithImportsImpl(psiExpression));
                    // add runtime imports if any
                    Set<String> imports = psiExpression.getUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY);
                    if (imports != null && res != null) {
                      if (res.getCustomInfo() != null) {
                        imports.add(res.getCustomInfo());
                      }
                      res = new XExpressionImpl(res.getExpression(), res.getLanguage(), StringUtil.join(imports, ","), res.getMode());
                    }
                    evaluationExpression = res;
                    result.setResult(res);
                  }).executeSynchronously();
                }
                else {
                  result.setError("Null");
                }
              });
          }
          catch (EvaluateException e) {
            LOG.info(e);
            result.setError(e);
          }
        }

        @Override
        protected void commandCancelled() {
          result.setError("Cancelled");
        }
      });
      return result;
    }
  }

  @Override
  @Nullable
  public String getValueText() {
    if (myValueDescriptor.getLastLabelRenderer() instanceof XValuePresentationProvider) {
      return null;
    }
    return myValueDescriptor.getValueText();
  }

  @Override
  public boolean shouldShowTextValue() {
    if (myValueDescriptor.isValueReady()) {
      return myValueDescriptor.isString();
    }
    return false;
  }

  @Nullable
  @Override
  public XReferrersProvider getReferrersProvider() {
    return new XReferrersProvider() {
      @Override
      public XValue getReferringObjectsValue() {
        ReferringObjectsProvider provider = ReferringObjectsProvider.BASIC_JDI;

        if (DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
          provider = new MemoryAgentPathsToClosestGCRootsProvider(
            MemoryAgent.DEFAULT_GC_ROOTS_PATHS_LIMIT,
            MemoryAgent.DEFAULT_GC_ROOTS_OBJECTS_LIMIT,
            provider
          );
        }

        return new JavaReferringObjectsValue(JavaValue.this, provider, null);
      }
    };
  }

  @Nullable
  @Override
  public XInstanceEvaluator getInstanceEvaluator() {
    return new XInstanceEvaluator() {
      @Override
      public void evaluate(@NotNull final XDebuggerEvaluator.XEvaluationCallback callback, @NotNull final XStackFrame frame) {
        myEvaluationContext.getManagerThread().schedule(new DebuggerCommandImpl() {
          @Override
          protected void commandCancelled() {
            callback.errorOccurred(JavaDebuggerBundle.message("error.context.has.changed"));
          }

          @Override
          protected void action() {
            ValueDescriptorImpl inspectDescriptor = myValueDescriptor;
            if (myValueDescriptor instanceof WatchItemDescriptor) {
              Modifier modifier = ((WatchItemDescriptor)myValueDescriptor).getModifier();
              if (modifier != null) {
                NodeDescriptor item = modifier.getInspectItem(getProject());
                if (item != null) {
                  inspectDescriptor = (ValueDescriptorImpl)item;
                }
              }
            }
            EvaluationContextImpl evaluationContext = ((JavaStackFrame)frame).getFrameDebuggerContext(null).createEvaluationContext();
            if (evaluationContext != null) {
              callback.evaluated(create(inspectDescriptor, evaluationContext, myNodeManager));
            }
            else {
              callback.errorOccurred(JavaDebuggerBundle.message("error.context.not.available"));
            }
          }
        });
      }
    };
  }

  public void setRenderer(NodeRenderer nodeRenderer, final XValueNodeImpl node) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myValueDescriptor.setRenderer(nodeRenderer);
    reBuild(node);
  }

  public void reBuild(final XValueNodeImpl node) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    node.invokeNodeUpdate(() -> {
      node.clearChildren();
      computePresentation(node, XValuePlace.TREE);
    });
  }

  private static class NavigateCommand extends SourcePositionCommand {
    NavigateCommand(final DebuggerContextImpl debuggerContext,
                    final ValueDescriptor descriptor,
                    final DebugProcessImpl debugProcess,
                    final AnActionEvent e) {
      super(debuggerContext, descriptor, debugProcess, e);
    }

    @Override
    protected NavigateCommand createRetryCommand() {
      return new NavigateCommand(myDebuggerContext, myDescriptor, myDebugProcess, myActionEvent);
    }

    @Override
    protected void doAction(final SourcePosition sourcePosition) {
      if (sourcePosition != null) {
        sourcePosition.navigate(true);
      }
    }
  }

  private abstract static class SourcePositionCommand extends SuspendContextCommandImpl {
    protected final DebuggerContextImpl myDebuggerContext;
    protected final ValueDescriptor myDescriptor;
    protected final DebugProcessImpl myDebugProcess;
    protected final AnActionEvent myActionEvent;

    SourcePositionCommand(final DebuggerContextImpl debuggerContext,
                          final ValueDescriptor descriptor,
                          final DebugProcessImpl debugProcess,
                          final AnActionEvent actionEvent) {
      super(debuggerContext.getSuspendContext());
      myDebuggerContext = debuggerContext;
      myDescriptor = descriptor;
      myDebugProcess = debugProcess;
      myActionEvent = actionEvent;
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) {
      try {
        doAction(calcPosition(myDescriptor, myDebugProcess));
      }
      catch (ClassNotLoadedException ex) {
        final String className = ex.className();
        if (loadClass(className) != null) {
          myDebugProcess.getManagerThread().schedule(createRetryCommand());
        }
      }
    }

    protected abstract SourcePositionCommand createRetryCommand();

    protected abstract void doAction(@Nullable SourcePosition sourcePosition);

    private ReferenceType loadClass(final String className) {
      final EvaluationContextImpl eContext = myDebuggerContext.createEvaluationContext();
      try {
        return myDebugProcess.loadClass(eContext, className, eContext.getClassLoader());
      }
      catch (Throwable ignored) {
      }
      return null;
    }

    private static SourcePosition calcPosition(final ValueDescriptor descriptor, final DebugProcessImpl debugProcess)
      throws ClassNotLoadedException {
      Type type = descriptor.getType();
      if (type == null) {
        return null;
      }

      try {
        if (type instanceof ArrayType arrayType) {
          type = arrayType.componentType();
        }
        if (type instanceof ClassType clsType) {

          Method lambdaMethod =
            MethodBytecodeUtil.getLambdaMethod(clsType, debugProcess.getVirtualMachineProxy().getClassesByNameProvider());
          Location location = lambdaMethod != null ? ContainerUtil.getFirstItem(DebuggerUtilsEx.allLineLocations(lambdaMethod)) : null;

          if (location == null) {
            location = ContainerUtil.getFirstItem(clsType.allLineLocations());
          }

          if (location != null) {
            SourcePosition position = debugProcess.getPositionManager().getSourcePosition(location);
            return ReadAction.compute(() -> {
              // adjust position for non-anonymous classes
              if (clsType.name().indexOf('$') < 0) {
                PsiClass classAt = JVMNameUtil.getClassAt(position);
                if (classAt != null) {
                  SourcePosition classPosition = SourcePosition.createFromElement(classAt);
                  if (classPosition != null) {
                    return classPosition;
                  }
                }
              }
              return position;
            });
          }
        }
      }
      catch (ClassNotPreparedException | AbsentInformationException e) {
        LOG.debug(e);
      }
      return null;
    }
  }
}
