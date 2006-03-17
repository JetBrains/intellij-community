/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Type;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class DomManager implements ProjectComponent {

  public static DomManager getDomManager(Project project) {
    return project.getComponent(DomManager.class);
  }

  public abstract Project getProject();

  @NotNull
  public abstract <T extends DomElement> DomFileElement<T> getFileElement(XmlFile file, Class<T> aClass, @NonNls String rootTagName);

  public abstract void addDomEventListener(DomEventListener listener);

  public abstract void removeDomEventListener(DomEventListener listener);

  public abstract DomGenericInfo getGenericInfo(Type type);

  public abstract <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass);

  @Nullable
  public abstract DomElement getDomElement(final XmlTag tag);

  public abstract Collection<PsiElement> getPsiElements(DomElement element);

  public abstract void registerPsiElementProvider(Function<DomElement, Collection<PsiElement>> provider);

  public abstract void unregisterPsiElementProvider(Function<DomElement, Collection<PsiElement>> provider);

  public abstract <T extends DomElement> T createMockElement(Class<T> aClass, final Module module, final boolean physical);

  public abstract boolean isMockElement(DomElement element);

  public abstract <T extends DomElement> T createStableValue(Factory<T> provider);
}
