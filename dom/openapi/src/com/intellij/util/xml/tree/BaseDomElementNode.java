package com.intellij.util.xml.tree;

import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.*;
import com.intellij.util.xml.ui.TooltipUtils;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;

import java.util.*;
import java.util.List;
import java.lang.reflect.Type;

import org.jetbrains.annotations.NotNull;


public class BaseDomElementNode extends AbstractDomElementNode {
  public static final Key<Comparator<AbstractDomElementNode>> COMPARATOR_KEY = Key.create("COMPARATOR_KEY");
  public static final Key<List<Class>> CONSOLIDATED_NODES_KEY = Key.create("CONSOLIDATED_NODES_KEY");
  public static final Key<List<Class>> FOLDER_NODES_KEY = Key.create("FOLDER_NODES_KEY");

  private final DomElement myDomElement;
  private final String myTagName;
  private final boolean folder;

  public BaseDomElementNode(final DomElement modelElement) {
    this(modelElement, null);
  }

  public BaseDomElementNode(final DomElement modelElement, SimpleNode parent) {
    super(modelElement, parent);

    myDomElement = modelElement;
    myTagName = modelElement.getXmlElementName();
    folder = isMarkedType(modelElement.getDomElementType(), FOLDER_NODES_KEY);
  }

  public SimpleNode[] getChildren() {
    return doGetChildren(myDomElement);
  }

  protected final SimpleNode[] doGetChildren(final DomElement element) {
    if (!element.isValid()) return NO_CHILDREN;

    List<SimpleNode> children = new ArrayList<SimpleNode>();
    final XmlTag tag = element.getXmlTag();
    final XmlElementDescriptor xmlElementDescriptor = tag == null ? null : tag.getDescriptor();
    final XmlElementDescriptor[] xmlDescriptors = xmlElementDescriptor == null ? null : xmlElementDescriptor.getElementsDescriptors(tag);

    for (DomFixedChildDescription description : element.getGenericInfo().getFixedChildrenDescriptions()) {
      String childName = description.getXmlElementName();
      if (xmlDescriptors != null) {
        boolean found = false;
        for (XmlElementDescriptor xmlDescriptor: xmlDescriptors) {
          if (xmlDescriptor.getDefaultName().equals(childName)) {
            found = true;
            break;
          }
        }
        if (!found) continue;
      }
      final List<? extends DomElement> values = description.getStableValues(element);
      if (shouldBeShown(description.getType())) {
        if (DomUtil.isGenericValueType(description.getType())) {
          for (DomElement value : values) {
            children.add(new GenericValueNode((GenericDomValue)value, this));
          }
        } else {
          for (DomElement domElement : values) {
            children.add(new BaseDomElementNode(domElement, this));
          }
        }
      }
    }

    for (DomCollectionChildDescription description : element.getGenericInfo().getCollectionChildrenDescriptions()) {
      if (shouldBeShown(description.getType())) {
        DomElementsGroupNode groupNode = new DomElementsGroupNode(element, description);
        if (isMarkedType(description.getType(), CONSOLIDATED_NODES_KEY)) {
          Collections.addAll(children, groupNode.getChildren());
        } else {
          children.add(groupNode);
        }
      }
    }

    AbstractDomElementNode[] childrenNodes = children.toArray(new AbstractDomElementNode[children.size()]);

    final Comparator<AbstractDomElementNode> comparator = myDomElement.getRoot().getUserData(COMPARATOR_KEY);
    if (comparator != null) {
      Arrays.sort(childrenNodes, comparator);
    }

    return childrenNodes;
  }

  @NotNull
  public List<DomCollectionChildDescription> getConsolidatedChildrenDescriptions() {
    final List<DomCollectionChildDescription> consolidated = new ArrayList<DomCollectionChildDescription>();
    for (DomCollectionChildDescription description : myDomElement.getGenericInfo().getCollectionChildrenDescriptions()) {
        if (isMarkedType(description.getType(), CONSOLIDATED_NODES_KEY)) {
          consolidated.add(description);
      }
    }
    return consolidated;
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myDomElement};
  }

  protected void doUpdate() {
    if (!myDomElement.isValid()) return;

    setUniformIcon(getNodeIcon());
    clearColoredText();

    final DomElementAnnotationsManager manager = DomElementAnnotationsManager.getInstance(myDomElement.getManager().getProject());
    final DomElementsProblemsHolder holder = manager.getProblemHolder(myDomElement);
    final List<DomElementProblemDescriptor> problems = holder.getProblems(myDomElement, true, highlightIfChildrenHasProblems(), HighlightSeverity.ERROR);

    if (problems.size() > 0) {
      addColoredFragment(getNodeName(), TooltipUtils.getTooltipText(problems), SimpleTextAttributes.ERROR_ATTRIBUTES);
    } else if (myDomElement.getXmlTag() == null && !(myDomElement instanceof DomFileElement)) {
      addColoredFragment(getNodeName(), folder ? SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    } else if (folder) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      final int childrenCount = getChildren().length;
      addColoredFragment(" (" + childrenCount + ')', SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  protected boolean isMarkedType(Type type, Key<List<Class>> key) {
    if (type == null) {
      return false;
    }
    final List<Class> classes = getDomElement().getRoot().getUserData(key);
    if (classes != null) {
      Class clazz = DomReflectionUtil.getRawType(type);
      return classes.contains(clazz);
    }
    return false;
  }

  protected boolean highlightIfChildrenHasProblems() {
    return true;
  }

  public String getNodeName() {
    final String name = myDomElement.getPresentation().getElementName();
    return name != null ? name : getPropertyName();
  }

  public String getTagName() {
    return myTagName;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public boolean isAutoExpandNode() {
    return getParent() == null;
  }
}
