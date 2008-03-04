package com.intellij.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.impl.cache.RepositoryManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class PsiManagerConfiguration implements ApplicationComponent, JDOMExternalizable {
  public boolean REPOSITORY_ENABLED = true;

  public static PsiManagerConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(PsiManagerConfiguration.class);
  }

  @NotNull
  public String getComponentName() {return "PsiManagerConfiguration"; }

  public void initComponent() { }

  public void disposeComponent() { }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public RepositoryManager createRepositoryManager(PsiManagerImpl manager) {
    return new EmptyRepositoryManagerImpl();
  }

  public RepositoryElementsManager createRepositoryElementsManager(PsiManagerImpl manager, RepositoryManager repositoryManager) {
    return new EmptyRepositoryElementsManager();
  }
}
