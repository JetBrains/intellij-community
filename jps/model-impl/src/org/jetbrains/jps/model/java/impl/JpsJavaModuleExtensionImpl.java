// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsUrlListRole;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.LanguageLevel;

import java.util.Objects;

final class JpsJavaModuleExtensionImpl extends JpsCompositeElementBase<JpsJavaModuleExtensionImpl> implements JpsJavaModuleExtension {
  private static final JpsUrlListRole JAVADOC_ROOTS_ROLE = new JpsUrlListRole("javadoc roots");
  private static final JpsUrlListRole ANNOTATIONS_ROOTS_ROLE = new JpsUrlListRole("annotation roots");
  private String myOutputUrl;
  private String myTestOutputUrl;
  private boolean myInheritOutput;
  private boolean myExcludeOutput;
  private LanguageLevel myLanguageLevel;

  JpsJavaModuleExtensionImpl() {
    myContainer.setChild(JAVADOC_ROOTS_ROLE);
    myContainer.setChild(ANNOTATIONS_ROOTS_ROLE);
  }

  private JpsJavaModuleExtensionImpl(JpsJavaModuleExtensionImpl original) {
    super(original);
    myOutputUrl = original.myOutputUrl;
    myTestOutputUrl = original.myTestOutputUrl;
    myLanguageLevel = original.myLanguageLevel;
  }

  @Override
  public @NotNull JpsJavaModuleExtensionImpl createCopy() {
    return new JpsJavaModuleExtensionImpl(this);
  }

  @Override
  public @NotNull JpsUrlList getAnnotationRoots() {
    return myContainer.getChild(ANNOTATIONS_ROOTS_ROLE);
  }

  @Override
  public @NotNull JpsUrlList getJavadocRoots() {
    return myContainer.getChild(JAVADOC_ROOTS_ROLE);
  }

  @Override
  public String getOutputUrl() {
    return myOutputUrl;
  }

  @Override
  public void setOutputUrl(String outputUrl) {
    if (!Objects.equals(myOutputUrl, outputUrl)) {
      myOutputUrl = outputUrl;
    }
  }

  @Override
  public String getTestOutputUrl() {
    return myTestOutputUrl;
  }

  @Override
  public void setTestOutputUrl(String testOutputUrl) {
    if (!Objects.equals(myTestOutputUrl, testOutputUrl)) {
      myTestOutputUrl = testOutputUrl;
    }
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (!Comparing.equal(myLanguageLevel, languageLevel)) {
      myLanguageLevel = languageLevel;
    }
  }

  @Override
  public boolean isInheritOutput() {
    return myInheritOutput;
  }

  @Override
  public void setInheritOutput(boolean inheritOutput) {
    if (myInheritOutput != inheritOutput) {
      myInheritOutput = inheritOutput;
    }
  }

  @Override
  public boolean isExcludeOutput() {
    return myExcludeOutput;
  }

  @Override
  public void setExcludeOutput(boolean excludeOutput) {
    if (myExcludeOutput != excludeOutput) {
      myExcludeOutput = excludeOutput;
    }
  }
}
