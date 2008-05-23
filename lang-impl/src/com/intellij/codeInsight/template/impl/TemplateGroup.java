package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.options.Scheme;

import java.util.ArrayList;
import java.util.Collection;

public class TemplateGroup implements Scheme {
  private final String myName;
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
}
