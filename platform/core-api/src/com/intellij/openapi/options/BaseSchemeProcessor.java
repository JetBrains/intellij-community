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
package com.intellij.openapi.options;

import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public abstract class BaseSchemeProcessor<T extends ExternalizableScheme> implements SchemeProcessor<T>, SchemeExtensionProvider {
  @Override
  public void initScheme(@NotNull T scheme) {
  }

  @Override
  public void onSchemeAdded(@NotNull T scheme) {
  }

  @Override
  public void onSchemeDeleted(@NotNull T scheme) {
  }

  @Override
  public void onCurrentSchemeChanged(@Nullable Scheme oldScheme) {
  }

  @Nullable
  public T readScheme(@NotNull Element element) throws InvalidDataException, IOException, JDOMException {
    return readScheme(new Document((Element)element.detach()));
  }

  @Nullable
  /**
   * @param duringLoad If occurred during {@link SchemesManager#loadSchemes()} call
   */
  public T readScheme(@NotNull Element element, boolean duringLoad) throws InvalidDataException, IOException, JDOMException {
    return readScheme(element);
  }

  @Override
  public T readScheme(@NotNull Document schemeContent) throws InvalidDataException, IOException, JDOMException {
    throw new AbstractMethodError();
  }

  public enum State {
    UNCHANGED, NON_PERSISTENT, POSSIBLY_CHANGED
  }

  @Override
  public boolean shouldBeSaved(@NotNull T scheme) {
    return true;
  }

  @NotNull
  public State getState(@NotNull T scheme) {
    return shouldBeSaved(scheme) ? State.POSSIBLY_CHANGED : State.NON_PERSISTENT;
  }

  @Override
  public boolean isUpgradeNeeded() {
    return false;
  }

  @NotNull
  @Override
  public String getSchemeExtension() {
    return ".xml";
  }
}
