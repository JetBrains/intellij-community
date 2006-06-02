/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsNavigationManagerImpl extends DomElementsNavigationManager implements ProjectComponent {
  private Map<String, DomElementNavigationProvider> myProviders = new HashMap<String, DomElementNavigationProvider>();
  private Project myProject;

  private DomElementNavigationProvider myTextEditorProvider = new MyDomElementNavigateProvider();

  public DomElementsNavigationManagerImpl(final Project project) {
    myProject = project;
    myProviders.put(myTextEditorProvider.getProviderName(), myTextEditorProvider);
  }

  public Set<DomElementNavigationProvider> getDomElementsNavigateProviders(DomElement domElement) {
    Set<DomElementNavigationProvider> result = new HashSet<DomElementNavigationProvider>();

    for (DomElementNavigationProvider navigateProvider : myProviders.values()) {
      if (navigateProvider.canNavigate(domElement)) result.add(navigateProvider) ;
    }
    return result;
  }

  public DomElementNavigationProvider getDomElementsNavigateProvider(String providerName) {
    return myProviders.get(providerName);
  }

  public void registerDomElementsNavigateProvider(DomElementNavigationProvider provider) {
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

  private class MyDomElementNavigateProvider extends DomElementNavigationProvider {

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
