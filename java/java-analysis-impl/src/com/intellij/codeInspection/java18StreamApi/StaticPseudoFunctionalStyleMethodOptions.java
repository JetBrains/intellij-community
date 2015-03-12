/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodOptions {
  private final MultiMap<String, PipelineElement> myIndex;

  public StaticPseudoFunctionalStyleMethodOptions() {
    myIndex = new MultiMap<String, PipelineElement>();
    restoreDefault();
  }

  public static class PipelineElement {
    private final String myHandlerClass;
    private final String myMethodName;
    private final String myStreamApiMethod;

    public PipelineElement(String handlerClass, String methodName, @Nullable String streamApiMethod) {
      myHandlerClass = handlerClass;
      myMethodName = methodName;
      myStreamApiMethod = streamApiMethod;
    }

    public String getHandlerClass() {
      return myHandlerClass;
    }

    public String getMethodName() {
      return myMethodName;
    }

    public String getStreamApiMethodName() {
      return myStreamApiMethod;
    }
  }

  @NotNull
  public Collection<PipelineElement> findElementsByMethodName(final String methodName) {
    return myIndex.get(methodName);
  }

  public void addElement(PipelineElement element) {
    myIndex.putValue(element.getMethodName(), element);
  }

  public void readExternal(final @NotNull Element element) {

  }


  public void writeExternal(final @NotNull Element element) {

  }

  private void restoreDefault() {
    myIndex.clear();
    final String guavaIterables = "com.google.common.collect.Iterables";
    addElement(new PipelineElement(guavaIterables, "transform", StreamApiConstants.MAP));
    addElement(new PipelineElement(guavaIterables, "filter", StreamApiConstants.FILTER));
    addElement(new PipelineElement(guavaIterables, "find", StreamApiConstants.FAKE_FIND_MATCHED));
    addElement(new PipelineElement(guavaIterables, "all", StreamApiConstants.ALL_MATCH));
    addElement(new PipelineElement(guavaIterables, "any", StreamApiConstants.ANY_MATCH));
  }
}
