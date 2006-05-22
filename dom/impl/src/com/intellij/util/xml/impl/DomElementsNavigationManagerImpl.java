/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.util.xml.DomElementNavigateProvider;
import com.intellij.util.xml.DomElement;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

import org.jetbrains.annotations.NonNls;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsNavigationManagerImpl extends DomElementsNavigationManager implements ProjectComponent {
  private Map<String, DomElementNavigateProvider> myProviders = new HashMap<String, DomElementNavigateProvider>();
  private Project myProject;

  private DomElementNavigateProvider myTextEditorProvider = new MyDomElementNavigateProvider();

  public DomElementsNavigationManagerImpl(final Project project) {
    myProject = project;
    myProviders.put(myTextEditorProvider.getProviderName(), myTextEditorProvider);
  }

  public Set<DomElementNavigateProvider> getDomElementsNavigateProviders(DomElement domElement) {
    Set<DomElementNavigateProvider> result = new HashSet<DomElementNavigateProvider>();

    for (DomElementNavigateProvider navigateProvider : myProviders.values()) {
      if (navigateProvider.canNavigate(domElement)) result.add(navigateProvider) ;
    }
    return result;
  }

  public DomElementNavigateProvider getDomElementsNavigateProvider(String providerName) {
    return myProviders.get(providerName);
  }

  public void registerDomElementsNavigateProvider(DomElementNavigateProvider provider) {
    myProviders.put(provider.getProviderName(), provider);
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  private class MyDomElementNavigateProvider implements DomElementNavigateProvider {

    public String getProviderName() {
      return DEFAULT_PROVIDER_NAME;
    }

    public void navigate(DomElement domElement, boolean requestFocus) {

      VirtualFile file = domElement.getRoot().getFile().getVirtualFile();
      final OpenFileDescriptor fileDescriptor = domElement.getXmlTag() != null ?
        new OpenFileDescriptor(myProject, file, domElement.getXmlTag().getTextOffset()) :
        new OpenFileDescriptor(myProject, file);

      FileEditorManagerEx.getInstanceEx(myProject).openTextEditor(fileDescriptor, requestFocus);
    }

    public boolean canNavigate(DomElement domElement) {
      return domElement != null && domElement.isValid();
    }
  }
}
