package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.options.Scheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class TemplateGroup implements Scheme {
  private String myName;
  private final Collection<TemplateImpl> myTemplates = new ArrayList<TemplateImpl>();

  public TemplateGroup(final String name) {
    myName = name;
  }

  public void addTemplate(TemplateImpl t) {
    myTemplates.add(t);
  }

  public Collection<TemplateImpl> getTemplates() {
    return myTemplates;
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
    for (TemplateImpl template : myTemplates) {
      template.setGroupName(name);
    }
  }

  public void removeTemplate(final TemplateImpl template) {
    for (Iterator templateIterator = myTemplates.iterator(); templateIterator.hasNext();) {
      TemplateImpl t = (TemplateImpl)templateIterator.next();
      if (t.getId() != null && t.getKey() != null && t.getId().equals(template.getId()) && t.getKey().equals(template.getKey())) {
        templateIterator.remove();
      }
    }
  }

  public boolean isEmpty() {
    return myTemplates.isEmpty();
  }
}
