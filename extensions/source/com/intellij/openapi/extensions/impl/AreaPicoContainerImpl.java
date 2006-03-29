/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.AreaPicoContainer;
import org.jetbrains.annotations.NonNls;
import org.picocontainer.*;
import org.picocontainer.alternatives.AbstractDelegatingMutablePicoContainer;
import org.picocontainer.defaults.ComponentAdapterFactory;
import org.picocontainer.defaults.DefaultComponentAdapterFactory;
import org.picocontainer.defaults.DefaultPicoContainer;

/**
 * @author Alexander Kireyev
 */
@SuppressWarnings({"unchecked"})
public class AreaPicoContainerImpl extends AbstractDelegatingMutablePicoContainer implements AreaPicoContainer {
  private ComponentAdapterFactory myComponentAdapterFactory;
  private final AreaInstance myAreaInstance;

  public AreaPicoContainerImpl(PicoContainer parentPicoContainer, AreaInstance areaInstance) {
    this(new MyPicoContainer(parentPicoContainer), areaInstance);
  }

  private AreaPicoContainerImpl(MyPicoContainer picoContainer, AreaInstance areaInstance) {
    super(picoContainer);
    myAreaInstance = areaInstance;
    picoContainer.setWrapperContainer(this);
  }

  public MutablePicoContainer makeChildContainer() {
    throw new UnsupportedOperationException("Method makeChildContainer() is not implemented");
  }

  public void setComponentAdapterFactory(ComponentAdapterFactory factory) {
    myComponentAdapterFactory = factory;
  }

  public ComponentAdapterFactory getComponentAdapterFactory() {
    return myComponentAdapterFactory;
  }


  @NonNls
  public String toString() {
    return "AreaPicoContainer[" + myAreaInstance + "]";
  }

  private static class MyPicoContainer extends DefaultPicoContainer {
    private AreaPicoContainerImpl myWrapperContainer;
    private ComponentAdapterFactory myDefault = new DefaultComponentAdapterFactory();

    public MyPicoContainer(final PicoContainer parentPicoContainer) {
      super(parentPicoContainer);
    }

    public void setWrapperContainer(AreaPicoContainerImpl wrapperContainer) {
      myWrapperContainer = wrapperContainer;
    }

    public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, Parameter[] parameters) throws PicoRegistrationException {
        ComponentAdapter componentAdapter = getAdapterFactory().createComponentAdapter(componentKey, componentImplementation, parameters);
        registerComponent(componentAdapter);
        return componentAdapter;
    }

    private ComponentAdapterFactory getAdapterFactory() {
      return myWrapperContainer.getComponentAdapterFactory() != null ? myWrapperContainer.getComponentAdapterFactory() : myDefault;
    }


    @NonNls
    @Override
    public String toString() {
      return "AreaPicoContainer.MyPicoContainer[" + myWrapperContainer.myAreaInstance + "]";
    }
  }
}
