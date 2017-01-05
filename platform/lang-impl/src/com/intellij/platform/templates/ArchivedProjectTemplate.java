/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 */
@Tag("template")
public abstract class ArchivedProjectTemplate implements ProjectTemplate {
  public static final String INPUT_FIELD = "input-field";
  public static final String TEMPLATE = "template";
  public static final String INPUT_DEFAULT = "default";

  protected final String myDisplayName;
  @Nullable private final String myCategory;

  private List<WizardInputField> myInputFields = Collections.emptyList();
  private List<String> myFrameworks = new ArrayList<>();
  private List<Artifact> myArtifacts = new ArrayList<>();

  public ArchivedProjectTemplate(@NotNull String displayName, @Nullable String category) {
    myDisplayName = displayName;
    myCategory = category;
  }

  @NotNull
  @Override
  public String getName() {
    return myDisplayName;
  }

  public Icon getIcon() {
    return getModuleType().getIcon();
  }

  protected abstract ModuleType getModuleType();

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new TemplateModuleBuilder(this, getModuleType(), getInputFields());
  }

  @NotNull
  public List<WizardInputField> getInputFields() {
    return myInputFields;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(elementTag = "artifact", surroundWithTag = false)
  public List<Artifact> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<Artifact> artifacts) {
    myArtifacts = artifacts;
  }

  @NotNull
  @Property(surroundWithTag = false)
  @AbstractCollection(elementTag = "framework", surroundWithTag = false, elementValueAttribute = "")
  public List<String> getFrameworks() {
    return myFrameworks;
  }

  public void setFrameworks(List<String> frameworks) {
    myFrameworks = frameworks;
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  public void handleUnzippedDirectories(File dir, List<File> filesToRefresh) throws IOException {
    filesToRefresh.add(dir);
  }

  public static abstract class StreamProcessor<T> {
    public abstract T consume(@NotNull ZipInputStream stream) throws IOException;
  }

  public abstract <T> T processStream(@NotNull StreamProcessor<T> consumer) throws IOException;

  @Nullable
  public String getCategory() {
    return myCategory;
  }

  public void populateFromElement(@NotNull Element element) {
    XmlSerializer.deserializeInto(this, element);
    myInputFields = getFields(element);
  }

  private static List<WizardInputField> getFields(Element templateElement) {
    //noinspection unchecked
    return ContainerUtil
      .mapNotNull(templateElement.getChildren(INPUT_FIELD), element -> {
        ProjectTemplateParameterFactory factory = WizardInputField.getFactoryById(element.getText());
        return factory == null ? null : factory.createField(element.getAttributeValue(INPUT_DEFAULT));
      });
  }

  static <T> T consumeZipStream(@NotNull StreamProcessor<T> consumer, @NotNull ZipInputStream stream) throws IOException {
    try {
      return consumer.consume(stream);
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }
}
