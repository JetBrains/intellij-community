/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.ArrayAction;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.memory.utils.ErrorsValueGroup;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorFactory;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.Collections;

public class ArrayRenderer extends NodeRendererImpl{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ArrayRenderer");

  public static final @NonNls String UNIQUE_ID = "ArrayRenderer";

  public int START_INDEX = 0;
  public int END_INDEX   = Integer.MAX_VALUE;
  public int ENTRIES_LIMIT = XCompositeNode.MAX_CHILDREN_TO_SHOW;

  private boolean myForced = false;

  public ArrayRenderer() {
    myProperties.setEnabled(true);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public @NonNls String getName() {
    return "Array";
  }

  public void setName(String text) {
    LOG.assertTrue(false);
  }

  public ArrayRenderer clone() {
    return (ArrayRenderer)super.clone();
  }

  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    return ClassRenderer.calcLabel(descriptor);
  }

  public void setForced(boolean forced) {
    myForced = forced;
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    NodeManagerImpl nodeManager = (NodeManagerImpl)builder.getNodeManager();
    NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

    ArrayReference array = (ArrayReference)value;
    int arrayLength = array.length();
    if (arrayLength > 0) {
      if (!myForced) {
        builder.initChildrenArrayRenderer(this, arrayLength);
      }

      if (ENTRIES_LIMIT <= 0) {
        ENTRIES_LIMIT = 1;
      }

      int added = 0;
      boolean hiddenNulls = false;
      int end = Math.min(arrayLength - 1, END_INDEX);
      int idx = START_INDEX;
      if (arrayLength > START_INDEX) {
        for (; idx <= end; idx++) {
          if (ViewsGeneralSettings.getInstance().HIDE_NULL_ARRAY_ELEMENTS && elementIsNull(array, idx)) {
            hiddenNulls = true;
            continue;
          }

          DebuggerTreeNode arrayItemNode =
            nodeManager.createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

          builder.addChildren(Collections.singletonList(arrayItemNode), false);
          added++;
          if (added >= ENTRIES_LIMIT) {
            break;
          }
        }
      }

      builder.addChildren(Collections.emptyList(), true);

      if (added == 0) {
        if (START_INDEX == 0 && arrayLength - 1 <= END_INDEX) {
          builder.setMessage(DebuggerBundle.message("message.node.all.elements.null"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
        }
        else {
          builder.setMessage(DebuggerBundle.message("message.node.all.array.elements.null", START_INDEX, END_INDEX), null,
                             SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
        }
      }
      else {
        if (hiddenNulls) {
          builder.setMessage(DebuggerBundle.message("message.node.elements.null.hidden"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
        }
        if (!myForced && idx < end) {
          builder.tooManyChildren(end - idx);
        }
      }
    }
  }

  private static boolean elementIsNull(ArrayReference arrayReference, int index) {
    try {
      return ArrayElementDescriptorImpl.getArrayElement(arrayReference, index) == null;
    }
    catch (EvaluateException e) {
      return false;
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) {
    LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl, node.getDescriptor().getClass().getName());
    ArrayElementDescriptorImpl descriptor = (ArrayElementDescriptorImpl)node.getDescriptor();

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(node.getProject()).getElementFactory();
    try {
      LanguageLevel languageLevel = LanguageLevelProjectExtension.getInstance(node.getProject()).getLanguageLevel();
      return elementFactory.createExpressionFromText("this[" + descriptor.getIndex() + "]", elementFactory.getArrayClass(languageLevel));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return value instanceof ArrayReference && ((ArrayReference)value).length() > 0;
  }

  public boolean isApplicable(Type type) {
    return type instanceof ArrayType;
  }

  public static class Filtered extends ArrayRenderer {
    private final XExpression myExpression;

    public Filtered(XExpression expression) {
      myExpression = expression;
    }

    public XExpression getExpression() {
      return myExpression;
    }

    @Override
    public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      NodeManagerImpl nodeManager = (NodeManagerImpl)builder.getNodeManager();
      NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

      builder.setMessage(DebuggerBundle.message("message.node.filtered") + " " + myExpression.getExpression(),
                         AllIcons.General.Filter,
                         SimpleTextAttributes.REGULAR_ATTRIBUTES,
                         FILTER_HYPERLINK);

      if (ENTRIES_LIMIT <= 0) {
        ENTRIES_LIMIT = 1;
      }

      ArrayReference array = (ArrayReference)value;
      int arrayLength = array.length();
      if (arrayLength > 0) {
        builder.initChildrenArrayRenderer(this, arrayLength);

        CachedEvaluator cachedEvaluator = new CachedEvaluator() {
          @Override
          protected String getClassName() {
            return ((ArrayType)array.type()).componentTypeName();
          }

          @Override
          protected PsiElement overrideContext(PsiElement context) {
            return ContextUtil.getContextElement(evaluationContext);
          }
        };
        cachedEvaluator.setReferenceExpression(TextWithImportsImpl.fromXExpression(myExpression));

        int added = 0;
        if (arrayLength - 1 >= START_INDEX) {
          ErrorsValueGroup errorsGroup = null;
          for (int idx = START_INDEX; idx < arrayLength; idx++) {
            try {
              if (DebuggerUtilsEx.evaluateBoolean(cachedEvaluator.getEvaluator(evaluationContext.getProject()),
                                                  (EvaluationContextImpl)evaluationContext.createEvaluationContext(array.getValue(idx)))) {

                DebuggerTreeNode arrayItemNode =
                  nodeManager
                    .createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

                builder.addChildren(Collections.singletonList(arrayItemNode), false);
                added++;
                //if (added > ENTRIES_LIMIT) {
                //  break;
                //}
              }
            }
            catch (EvaluateException e) {
              if (errorsGroup == null) {
                errorsGroup = new ErrorsValueGroup();
                builder.addChildren(XValueChildrenList.bottomGroup(errorsGroup), false);
              }
              JavaValue childValue = JavaValue
                .create(null,
                        (ValueDescriptorImpl)descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx),
                        ((EvaluationContextImpl)evaluationContext),
                        nodeManager,
                        false);
              errorsGroup.addErrorValue(e.getMessage(), childValue);
            }
          }
        }

        builder.addChildren(Collections.emptyList(), true);

        //if (added != 0 && END_INDEX < arrayLength - 1) {
        //  builder.setRemaining(arrayLength - 1 - END_INDEX);
        //}
      }
    }

    public static final XDebuggerTreeNodeHyperlink FILTER_HYPERLINK = new XDebuggerTreeNodeHyperlink(" clear") {
      @Override
      public void onClick(MouseEvent e) {
        XDebuggerTree tree = (XDebuggerTree)e.getSource();
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          TreeNode parent = ((TreeNode)path.getLastPathComponent()).getParent();
          if (parent instanceof XValueNodeImpl) {
            XValueNodeImpl valueNode = (XValueNodeImpl)parent;
            ArrayAction.setArrayRenderer(NodeRendererSettings.getInstance().getArrayRenderer(),
                                         valueNode,
                                         DebuggerManagerEx.getInstanceEx(tree.getProject()).getContext());
          }
        }
        e.consume();
      }
    };
  }
}
