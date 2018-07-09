/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author nik
 */
public class JavaSourceRootProperties extends JpsElementBase<JavaSourceRootProperties> implements JpsSimpleElement<JavaSourceRootProperties> {
  private String myPackagePrefix = "";
  private boolean myForGeneratedSources;

  /**
   * @deprecated do not call this method directly, use {@link org.jetbrains.jps.model.java.JpsJavaExtensionService#createSourceRootProperties(String)} instead
   */
  @Deprecated
  public JavaSourceRootProperties() {
  }

  /**
   * @deprecated do not call this method directly, use {@link org.jetbrains.jps.model.java.JpsJavaExtensionService#createSourceRootProperties(String)} instead
   */
  @Deprecated
  public JavaSourceRootProperties(@NotNull String packagePrefix) {
    myPackagePrefix = packagePrefix;
  }

  /**
   * @deprecated do not call this method directly, use {@link org.jetbrains.jps.model.java.JpsJavaExtensionService#createSourceRootProperties(String, boolean)} instead
   */
  @Deprecated
  public JavaSourceRootProperties(@NotNull String packagePrefix, boolean forGeneratedSources) {
    myPackagePrefix = packagePrefix;
    myForGeneratedSources = forGeneratedSources;
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @NotNull
  @Override
  public JavaSourceRootProperties createCopy() {
    return new JavaSourceRootProperties(myPackagePrefix, myForGeneratedSources);
  }

  public boolean isForGeneratedSources() {
    return myForGeneratedSources;
  }

  public void setPackagePrefix(@NotNull String packagePrefix) {
    if (!Comparing.equal(myPackagePrefix, packagePrefix)) {
      myPackagePrefix = packagePrefix;
      fireElementChanged();
    }
  }

  public void setForGeneratedSources(boolean forGeneratedSources) {
    if (myForGeneratedSources != forGeneratedSources) {
      myForGeneratedSources = forGeneratedSources;
      fireElementChanged();
    }
  }

  @Override
  public void applyChanges(@NotNull JavaSourceRootProperties modified) {
    setPackagePrefix(modified.myPackagePrefix);
    setForGeneratedSources(modified.myForGeneratedSources);
  }

  /**
   * @deprecated use {@link #setPackagePrefix(String)} instead
   */
  @Deprecated
  @Override
  public void setData(@NotNull JavaSourceRootProperties data) {
    applyChanges(data);
  }

  /**
   * @deprecated use {@link #getPackagePrefix()} instead
   */
  @Deprecated
  @NotNull
  @Override
  public JavaSourceRootProperties getData() {
    return this;
  }
}
