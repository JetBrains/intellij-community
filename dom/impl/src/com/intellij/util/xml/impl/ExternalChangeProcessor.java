/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.xml.events.ElementChangedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ExternalChangeProcessor implements XmlChangeVisitor {
  private static final TagChangeSet NULL_CHANGE_SET = new TagChangeSet(null){
    protected XmlTag getXmlTag(final DomInvocationHandler handler) {
      return null;
    }

    public void addAdded(XmlElement child) {
    }

    public void addAttributeChanged(String s) {
    }

    public void addChanged(XmlElement child) {
    }

    public void addRemoved(XmlElement child) {
    }
  };

  private Map<XmlTag,TagChangeSet> myChangeSets = new HashMap<XmlTag, TagChangeSet>();
  private boolean myDocumentChanged;
  private final DomManagerImpl myDomManager;

  public ExternalChangeProcessor(DomManagerImpl domManager, XmlChangeSet changeSet) {
    myDomManager = domManager;
    for (XmlChange xmlChange : changeSet.getChanges()) {
      xmlChange.accept(this);
    }
  }

  public void processChanges() {
    for (TagChangeSet changeSet : myChangeSets.values()) {
      changeSet.processChanges();
    }
  }

  private TagChangeSet getChangeSet(XmlTag tag) {
    assert !myDocumentChanged;
    assert tag != null;
    TagChangeSet changeSet = myChangeSets.get(tag);
    if (changeSet == null) {
      DomInvocationHandler handler = DomManagerImpl.getCachedElement(tag);
      if (handler != null) {
        changeSet = new TagChangeSet(handler);
        myChangeSets.put(tag, changeSet);
      } else {
        changeSet = NULL_CHANGE_SET;
      }
    }
    return changeSet;
  }

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    getChangeSet(xmlAttributeSet.getTag()).addAttributeChanged(xmlAttributeSet.getName());
  }

  public void visitDocumentChanged(final XmlDocumentChanged change) {
    assert myChangeSets.isEmpty();
    documentChanged((XmlFile)change.getDocument().getContainingFile());
  }

  private void documentChanged(final XmlFile xmlFile) {
    myDocumentChanged = true;
    final DomFileElementImpl element = myDomManager.getFileElement(xmlFile);
    if (element != null) {
      final DomInvocationHandler rootHandler = element.getRootHandler();
      rootHandler.detach(false);
      final XmlTag rootTag = element.getRootTag();
      if (rootTag != null) {
        rootHandler.attach(rootTag);
      }
      element.getManager().fireEvent(new ElementChangedEvent(element.getRootElement()));
    }
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    final XmlElement element = xmlElementChanged.getElement();
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlTag) {
      getChangeSet((XmlTag)parent).addChanged(element);
    }
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    getChangeSet(xmlTagChildAdd.getTag()).addAdded(xmlTagChildAdd.getChild());
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    getChangeSet(xmlTagChildChanged.getTag()).addChanged(xmlTagChildChanged.getChild());
  }

  public void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved) {
    getChangeSet(xmlTagChildRemoved.getTag()).addRemoved(xmlTagChildRemoved.getChild());
  }

  public void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    final XmlTag tag = xmlTagNameChanged.getTag();
    final XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      getChangeSet(parentTag).addChanged(tag);
    } else {
      final PsiFile file = tag.getContainingFile();
      if (file instanceof XmlFile) {
        documentChanged((XmlFile)file);
      }
    }
  }

  public void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    final XmlText text = xmlTextChanged.getText();
    getChangeSet(text.getParentTag()).addChanged(text);
  }
}
