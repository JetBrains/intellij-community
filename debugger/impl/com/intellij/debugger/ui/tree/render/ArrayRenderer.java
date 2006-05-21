package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorFactory;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 3:07:19 PM
 */
public class ArrayRenderer extends NodeRendererImpl{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ArrayRenderer");
  
  public static final @NonNls String UNIQUE_ID = "ArrayRenderer";

  public int START_INDEX = 0;
  public int END_INDEX   = 100;
  public int ENTRIES_LIMIT = 100;
  private final static String MORE_ELEMENTS = "...";

  public ArrayRenderer() {
    myProperties.setEnabled(true);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
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

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<DebuggerTreeNode> children = new ArrayList<DebuggerTreeNode>();
    NodeManagerImpl nodeManager = (NodeManagerImpl)builder.getNodeManager();
    NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

    ArrayReference array = (ArrayReference)value;
    if (array.length() > 0) {
      int added = 0;

      if(ENTRIES_LIMIT > END_INDEX - START_INDEX + 1) {
        ENTRIES_LIMIT = END_INDEX - START_INDEX;
      }

      if(ENTRIES_LIMIT <= 0) {
        ENTRIES_LIMIT = 1;
      }

      if(array.length() - 1 >= START_INDEX) {
        int start = START_INDEX;
        int end  = array.length() - 1 < END_INDEX   ? array.length() - 1 : END_INDEX;

        int idx;

        for (idx = start; idx <= end; idx++) {
          DebuggerTreeNode arrayItemNode = nodeManager.createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

          if (ViewsGeneralSettings.getInstance().HIDE_NULL_ARRAY_ELEMENTS && ((ValueDescriptorImpl)arrayItemNode.getDescriptor()).isNull()) continue;
          if(added >= (ENTRIES_LIMIT  + 1)/ 2) break;
          children.add(arrayItemNode);
          added++;
        }

        start = idx;

        List<DebuggerTreeNode> childrenTail = new ArrayList<DebuggerTreeNode>();
        for (idx = end; idx >= start; idx--) {
          DebuggerTreeNode arrayItemNode = nodeManager.createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

          if (ViewsGeneralSettings.getInstance().HIDE_NULL_ARRAY_ELEMENTS && ((ValueDescriptorImpl)arrayItemNode.getDescriptor()).isNull()) continue;
          if(added >= ENTRIES_LIMIT) break;
          childrenTail.add(arrayItemNode);
          added++;
        }

        //array is printed in the following way
        // ...
        // items1...itemENTRIES_LIMIT/2
        // ...
        // itemENTRIES_LIMIT/2+1...itemENTRIES_LIMIT
        // ...

        //when itemENTRIES_LIMIT/2+1...itemENTRIES_LIMIT set is empty, we should not add middle "..." node
        if(idx >= start && !(ENTRIES_LIMIT == 1 && END_INDEX < array.length())) {
          children.add(nodeManager.createMessageNode(new MessageDescriptor(MORE_ELEMENTS, MessageDescriptor.SPECIAL)));
        }

        for (ListIterator<DebuggerTreeNode> iterator = childrenTail.listIterator(childrenTail.size()); iterator.hasPrevious();) {
          DebuggerTreeNode debuggerTreeNode = iterator.previous();
          children.add(debuggerTreeNode);
        }
      }

      if (added == 0) {
        if(START_INDEX == 0 && array.length() - 1 <= END_INDEX) {
          children.add(nodeManager.createMessageNode(MessageDescriptor.ALL_ELEMENTS_IN_RANGE_ARE_NULL.getLabel()));
        }
        else {
          children.add(nodeManager.createMessageNode(MessageDescriptor.ALL_ELEMENTS_IN_VISIBLE_RANGE_ARE_NULL.getLabel()));
        }
      }
      else {
        if(START_INDEX > 0) {
          children.add(0, nodeManager.createMessageNode(new MessageDescriptor(MORE_ELEMENTS, MessageDescriptor.SPECIAL)));
        }

        if(END_INDEX < array.length() - 1) {
          children.add(nodeManager.createMessageNode(new MessageDescriptor(MORE_ELEMENTS, MessageDescriptor.SPECIAL)));
        }
      }
    }
    builder.setChildren(children);
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

    final PsiManager manager = PsiManager.getInstance(node.getProject());
    PsiElementFactory elementFactory = manager.getElementFactory();
    try {
      LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
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
    return (type instanceof ArrayType);
  }
}
