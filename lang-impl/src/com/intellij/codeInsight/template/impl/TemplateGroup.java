package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.ExternalizableScheme;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class TemplateGroup implements ExternalizableScheme {
  private String myName;
  private final Collection<TemplateImpl> myTemplates = new ArrayList<TemplateImpl>();
  private ExternalInfo myExternalInfo = new ExternalInfo();

  public boolean blocked = false;

  public TemplateGroup(final String name) {
    myName = name;
  }

  public void addTemplate(TemplateImpl t) {
    if (blocked) {
      System.out.println("");
    }
    myTemplates.add(t);
  }

  public Collection<TemplateImpl> getTemplates() {
    return Collections.unmodifiableCollection(myTemplates);
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
    if (blocked) {
      System.out.println("");
    }
    
    for (Iterator templateIterator = myTemplates.iterator(); templateIterator.hasNext();) {
      TemplateImpl t = (TemplateImpl)templateIterator.next();
      if (t.getKey() != null && t.getKey().equals(template.getKey())) {
        templateIterator.remove();
      }
    }
  }

  public boolean isEmpty() {
    return myTemplates.isEmpty();
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }

  public TemplateGroup copy() {
    TemplateGroup result = new TemplateGroup(getName());
    for (TemplateImpl template : myTemplates) {
      result.addTemplate(template.copy());
    }
    result.getExternalInfo().copy(getExternalInfo());
    return result;
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean contains(final TemplateImpl template) {
    for (TemplateImpl t : myTemplates) {
      if (t.getKey() != null && t.getKey().equals(template.getKey())) {
        return true;
      }
    }
    return false;
  }
}
