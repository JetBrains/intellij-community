/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.javaee.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class ElementPresentationManager {
  private static final List<ElementPresentationFactory> ourPresentationFactories = new ArrayList<ElementPresentationFactory>();

  public static void registerPresentationFactory(ElementPresentationFactory factory) {
    ourPresentationFactories.add(factory);
  }

  public static void unregisterPresentationFactory(ElementPresentationFactory factory) {
    ourPresentationFactories.remove(factory);
  }

  @Nullable
  public static <T> ElementPresentation getElementPresentation(T element) {
    for (final ElementPresentationFactory factory : ourPresentationFactories) {
      final ElementPresentation presentation = factory.getPresentation(element);
      if (presentation != null) {
        return presentation;
      }
    }
    return null;
  }
}
