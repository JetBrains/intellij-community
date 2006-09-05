package com.intellij.util.xml.tree;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DomElementsGroupNode extends AbstractDomElementNode {
  private DomElement myParentElement;
  private String myChildrenTagName;
  private DomCollectionChildDescription myChildDescription;

  public DomElementsGroupNode(final DomElement modelElement, DomCollectionChildDescription description) {
    super(modelElement);
    myParentElement = modelElement;
    myChildDescription = description;
    myChildrenTagName = description.getXmlElementName();
  }

  public SimpleNode[] getChildren() {
    if (!myParentElement.isValid()) return NO_CHILDREN;

    final List<SimpleNode> simpleNodes = new ArrayList<SimpleNode>();
    for (DomElement domChild : myChildDescription.getStableValues(myParentElement)) {
      if (shouldBeShown(domChild.getDomElementType())) {
        simpleNodes.add(new BaseDomElementNode(domChild, this));
      }
    }
    return simpleNodes.toArray(new SimpleNode[simpleNodes.size()]);
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myParentElement, myChildrenTagName};
  }

  protected void doUpdate() {
    setUniformIcon(getNodeIcon());

    clearColoredText();

    final boolean showErrors = hasErrors();
    final int childrenCount = getChildren().length;

    if (childrenCount > 0) {
      final SimpleTextAttributes textAttributes =
        showErrors ? getWavedAttributes(SimpleTextAttributes.STYLE_BOLD) : SimpleTextAttributes.REGULAR_ATTRIBUTES;

      addColoredFragment(getNodeName(), textAttributes);
      addColoredFragment(" (" + childrenCount + ')', showErrors ? IdeBundle.message("dom.elements.tree.childs.contain.errors") : null,
                         SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
  }

  private boolean hasErrors() {
    for (DomElement domElement : myChildDescription.getStableValues(myParentElement)) {
      final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(getProject());
      final DomElementsProblemsHolder holder = annotationsManager.getCachedProblemHolder(domElement);
      final List<DomElementProblemDescriptor> problems = holder.getProblems(domElement, true, true, HighlightSeverity.ERROR);
      if (problems.size() > 0) return true;
    }

    return false;
  }

  public String getNodeName() {
    return myChildDescription.getCommonPresentableName(myParentElement);
  }

  public String getTagName() {
    return myChildrenTagName;
  }

  public DomElement getDomElement() {
    return myParentElement;
  }


  public DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }


  public Icon getNodeIcon() {
    Class clazz = DomReflectionUtil.getRawType(myChildDescription.getType());
//        Class arrayClass = Array.newInstance(clazz, 0).getClass();
    return ElementPresentationManager.getIconForClass(clazz);
  }
}
