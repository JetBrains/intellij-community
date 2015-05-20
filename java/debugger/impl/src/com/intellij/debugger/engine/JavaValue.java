/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.JavaReferringObjectsValue;
import com.intellij.debugger.actions.JumpToObjectAction;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.ToStringBasedRenderer;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.*;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.evaluate.XValueCompactPresentation;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.XValueExtendedPresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
* @author egor
*/
public class JavaValue extends XNamedValue implements NodeDescriptorProvider, XValueTextProvider {
  private static final Logger LOG = Logger.getInstance(JavaValue.class);

  private final JavaValue myParent;
  private final ValueDescriptorImpl myValueDescriptor;
  private final EvaluationContextImpl myEvaluationContext;
  private final NodeManagerImpl myNodeManager;
  private final boolean myContextSet;

  protected JavaValue(JavaValue parent,
                    @NotNull ValueDescriptorImpl valueDescriptor,
                    @NotNull EvaluationContextImpl evaluationContext,
                    NodeManagerImpl nodeManager,
                    boolean contextSet) {
    super(valueDescriptor.getName());
    myParent = parent;
    myValueDescriptor = valueDescriptor;
    myEvaluationContext = evaluationContext;
    myNodeManager = nodeManager;
    myContextSet = contextSet;
  }

  static JavaValue create(JavaValue parent,
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

  public EvaluationContextImpl getEvaluationContext() {
    return myEvaluationContext;
  }

  public NodeManagerImpl getNodeManager() {
    return myNodeManager;
  }

  @Override
  public void computePresentation(@NotNull final XValueNode node, @NotNull XValuePlace place) {
    final SuspendContextImpl suspendContext = myEvaluationContext.getSuspendContext();
    myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      protected void commandCancelled() {
        node.setPresentation(null, new JavaValuePresentation("", null, DebuggerBundle.message("error.context.has.changed"), myValueDescriptor), false);
      }

      @Override
      public void contextAction() throws Exception {
        if (!myContextSet) {
          myValueDescriptor.setContext(myEvaluationContext);
        }
        myValueDescriptor.updateRepresentation(myEvaluationContext, new DescriptorLabelListener() {
          @Override
          public void labelChanged() {
            Icon nodeIcon = DebuggerTreeRenderer.getValueIcon(myValueDescriptor);
            final String value = getValueString();
            XValuePresentation presentation;
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            EvaluateException exception = myValueDescriptor.getEvaluateException();
            presentation = new JavaValuePresentation(value, myValueDescriptor.getIdLabel(), exception != null ? exception.getMessage() : null, myValueDescriptor);

            if (myValueDescriptor.getLastRenderer() instanceof FullValueEvaluatorProvider) {
              XFullValueEvaluator evaluator = ((FullValueEvaluatorProvider)myValueDescriptor.getLastRenderer())
                .getFullValueEvaluator(myEvaluationContext, myValueDescriptor);
              if (evaluator != null) {
                node.setFullValueEvaluator(evaluator);
              }
            }
            else if (value.length() > XValueNode.MAX_VALUE_LENGTH) {
              node.setFullValueEvaluator(new XFullValueEvaluator() {
                @Override
                public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
                  myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
                    @Override
                    public Priority getPriority() {
                      return Priority.NORMAL;
                    }

                    @Override
                    protected void commandCancelled() {
                      callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
                    }

                    @Override
                    public void contextAction() throws Exception {
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
              });
            }
            node.setPresentation(nodeIcon, presentation, myValueDescriptor.isExpandable());
          }
        });
      }
    });
  }

  private static String truncateToMaxLength(String value) {
    return value.substring(0, Math.min(value.length(), XValueNode.MAX_VALUE_LENGTH));
  }

  private static class JavaValuePresentation extends XValueExtendedPresentation implements XValueCompactPresentation {
    private final String myValue;
    private final String myType;
    private final String myError;
    private final ValueDescriptorImpl myValueDescriptor;

    public JavaValuePresentation(@NotNull String value, @Nullable String type, @Nullable String error, ValueDescriptorImpl valueDescriptor) {
      myValue = value;
      myType = type;
      myError = error;
      myValueDescriptor = valueDescriptor;
    }

    @Nullable
    @Override
    public String getType() {
      return StringUtil.nullize(myType);
    }

    @Override
    public void renderValue(@NotNull XValueTextRenderer renderer) {
      renderValue(renderer, null);
    }

    @Override
    public void renderValue(@NotNull XValueTextRenderer renderer, @Nullable XValueNodeImpl node) {
      boolean compact = node != null;
      if (myError != null) {
        if (myValue.endsWith(myError)) {
          renderer.renderValue(myValue.substring(0, myValue.length() - myError.length()));
        }
        renderer.renderError(myError);
      }
      else {
        if (compact && node.getValueContainer() instanceof JavaValue) {
          final JavaValue container = (JavaValue)node.getValueContainer();

          if (container.getDescriptor().isArray()) {
            final ArrayReference value = (ArrayReference)container.getDescriptor().getValue();
            final ArrayType type = (ArrayType)container.getDescriptor().getType();
            if (type != null) {
              final String typeName = type.componentTypeName();
              if (TypeConversionUtil.isPrimitive(typeName) || CommonClassNames.JAVA_LANG_STRING.equals(typeName)) {
                int size = value.length();
                int max = Math.min(size, CommonClassNames.JAVA_LANG_STRING.equals(typeName) ? 5 : 10);
                //TODO [eu]: this is a quick fix for IDEA-136606, need to move this away from EDT!!!
                final List<Value> values = value.getValues(0, max);
                int i = 0;
                final List<String> vals = new ArrayList<String>(max);
                while (i < values.size()) {
                  vals.add(StringUtil.first(values.get(i).toString(), 15, true));
                  i++;
                }
                String more = "";
                if (vals.size() < size) {
                  more = ", + " + (size - vals.size()) + " more";
                }

                renderer.renderValue("{" + StringUtil.join(vals, ", ") + more + "}");
                return;
              }
            }
          }
        }

        String value = myValue;
        if (myValueDescriptor.isString()) {
          renderer.renderStringValue(myValue, "\"\\", XValueNode.MAX_VALUE_LENGTH);
          return;
        }
        else if (myValueDescriptor.getLastRenderer() instanceof ToStringRenderer ||
                 myValueDescriptor.getLastRenderer() instanceof ToStringBasedRenderer) {
          value = StringUtil.wrapWithDoubleQuote(truncateToMaxLength(myValue));
        }
        else if (myValueDescriptor.getLastRenderer() instanceof CompoundReferenceRenderer) {
          value = truncateToMaxLength(myValue);
        }
        renderer.renderValue(value);
      }
    }

    @NotNull
    @Override
    public String getSeparator() {
      String fullName = myValueDescriptor.calcValueName();
      String name = myValueDescriptor.getName();
      if (!StringUtil.isEmpty(fullName) && !name.equals(fullName) && fullName.startsWith(name)) {
        return fullName.substring(name.length()) + " " + DEFAULT_SEPARATOR;
      }
      return DEFAULT_SEPARATOR;
    }

    @Override
    public boolean isModified() {
      return myValueDescriptor.isDirty();
    }
  }

  @NotNull
  String getValueString() {
    return myValueDescriptor.getValueText();
  }

  private int myCurrentChildrenStart = 0;

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    scheduleCommand(myEvaluationContext, node, new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void contextAction() throws Exception {
        final XValueChildrenList children = new XValueChildrenList();
        final NodeRenderer renderer = myValueDescriptor.getRenderer(myEvaluationContext.getDebugProcess());
        final Ref<Integer> remainingNum = new Ref<Integer>(0);
        renderer.buildChildren(myValueDescriptor.getValue(), new ChildrenBuilder() {
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
          public void setRemaining(int remaining) {
            remainingNum.set(remaining);
          }

          @Override
          public void initChildrenArrayRenderer(ArrayRenderer renderer) {
            renderer.START_INDEX = myCurrentChildrenStart;
            renderer.END_INDEX = myCurrentChildrenStart + XCompositeNode.MAX_CHILDREN_TO_SHOW - 1;
            myCurrentChildrenStart += XCompositeNode.MAX_CHILDREN_TO_SHOW;
          }

          @Override
          public void setChildren(List<DebuggerTreeNode> nodes) {
            for (DebuggerTreeNode node : nodes) {
              final NodeDescriptor descriptor = node.getDescriptor();
              if (descriptor instanceof ValueDescriptorImpl) {
                // Value is calculated already in NodeManagerImpl
                children.add(create(JavaValue.this, (ValueDescriptorImpl)descriptor, myEvaluationContext, myNodeManager, false));
              }
              else if (descriptor instanceof MessageDescriptor) {
                children.add(new JavaStackFrame.DummyMessageValueNode(descriptor.getLabel(), null));
              }
            }
          }
        }, myEvaluationContext);
        node.addChildren(children, true);
        if (remainingNum.get() > 0) {
          node.tooManyChildren(remainingNum.get());
        }
      }
    });
  }

  protected static boolean scheduleCommand(EvaluationContextImpl evaluationContext,
                                        @NotNull final XCompositeNode node,
                                        final SuspendContextCommandImpl command) {
    evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(command.getSuspendContext()) {
      @Override
      public void contextAction() throws Exception {
        command.contextAction();
      }

      @Override
      protected void commandCancelled() {
        node.setErrorMessage(DebuggerBundle.message("error.context.has.changed"));
      }
    });
    return true;
  }

  @Override
  public void computeSourcePosition(@NotNull final XNavigatable navigatable) {
    if (navigatable instanceof XInlineSourcePosition && !(navigatable instanceof XNearestSourcePosition)
        && !(myValueDescriptor instanceof ThisDescriptorImpl || myValueDescriptor instanceof LocalVariableDescriptor)) {
      return;
    }
    myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public Priority getPriority() {
        if (navigatable instanceof XInlineSourcePosition) {
          return Priority.LOWEST;
        }
        return Priority.NORMAL;
      }

      @Override
      protected void commandCancelled() {
        navigatable.setSourcePosition(null);
      }

      @Override
      public void contextAction() throws Exception {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final boolean nearest = navigatable instanceof XNearestSourcePosition;
            SourcePosition position = SourcePositionProvider.getSourcePosition(myValueDescriptor, getProject(), getDebuggerContext(), nearest);
            if (position != null) {
              navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position));
            }
          }
        });
      }
    });
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
    debugProcess.getManagerThread().schedule(new JumpToObjectAction.NavigateCommand(getDebuggerContext(), myValueDescriptor, debugProcess, null) {
      @Override
      public Priority getPriority() {
        return Priority.HIGH;
      }

      @Override
      protected void doAction(@Nullable final SourcePosition sourcePosition) {
        if (sourcePosition != null) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(sourcePosition));
            }
          });
        }
      }
    });
  }

  @Nullable
  @Override
  public XValueModifier getModifier() {
    return myValueDescriptor.canSetValue() ? new JavaValueModifier(this) : null;
  }

  private volatile String evaluationExpression = null;

  @NotNull
  @Override
  public Promise<String> calculateEvaluationExpression() {
    if (evaluationExpression != null) {
      return Promise.resolve(evaluationExpression);
    }
    else {
      final AsyncPromise<String> res = new AsyncPromise<String>();
      myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.HIGH;
        }

        @Override
        public void contextAction() throws Exception {
          evaluationExpression = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Override
            public String compute() {
              try {
                PsiExpression psiExpression = getDescriptor().getTreeEvaluation(JavaValue.this, getDebuggerContext());
                if (psiExpression != null) {
                  return new TextWithImportsImpl(psiExpression).getText();
                }
              }
              catch (EvaluateException e) {
                LOG.info(e);
              }
              return null;
            }
          });
          res.setResult(evaluationExpression);
        }
      });
      return res;
    }
  }

  @Override
  public String getValueText() {
    return myValueDescriptor.getValueText();
  }
  @Nullable
  @Override
  public XReferrersProvider getReferrersProvider() {
    return new XReferrersProvider() {
      @Override
      public XValue getReferringObjectsValue() {
        return new JavaReferringObjectsValue(JavaValue.this, false);
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
            callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
          }

          @Override
          protected void action() throws Exception {
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
            EvaluationContextImpl evaluationContext = ((JavaStackFrame)frame).getFrameDebuggerContext().createEvaluationContext();
            if (evaluationContext != null) {
              callback.evaluated(create(inspectDescriptor, evaluationContext, myNodeManager));
            }
            else {
              callback.errorOccurred("Context is not available");
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
    myCurrentChildrenStart = 0;
    node.getTree().getLaterInvocator().offer(new Runnable() {
      @Override
      public void run() {
        node.clearChildren();
        computePresentation(node, XValuePlace.TREE);
      }
    });
  }
}
