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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@State(
  name = "EqualsHashCodeTemplates",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/equalsHashCodeTemplates.xml"
    )}
)
public class EqualsHashCodeTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_EQUALS = "/com/intellij/codeInsight/generation/defaultEquals.vm";
  private static final String DEFAULT_HASH_CODE = "/com/intellij/codeInsight/generation/defaultHashCode.vm";
  private static final String APACHE3_EQUALS = "/com/intellij/codeInsight/generation/apacheEqualsBuilder3.vm";
  private static final String APACHE3_HASH_CODE = "/com/intellij/codeInsight/generation/apacheHashCodeBuilder3.vm";
  private static final String APACHE_EQUALS = "/com/intellij/codeInsight/generation/apacheEqualsBuilder.vm";
  private static final String APACHE_HASH_CODE = "/com/intellij/codeInsight/generation/apacheHashCodeBuilder.vm";
  private static final String GUAVA_EQUALS = "/com/intellij/codeInsight/generation/guavaEquals.vm";
  private static final String GUAVA_HASH_CODE = "/com/intellij/codeInsight/generation/guavaHashCode.vm";

  private static final String EQUALS_SUFFIX = "equals";
  private static final String HASH_CODE_SUFFIX = "hashCode";


  public static EqualsHashCodeTemplatesManager getInstance() {
    return ServiceManager.getService(EqualsHashCodeTemplatesManager.class);
  }

  @Override
  public TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[] {
        new TemplateResource("IntelliJ Default equals", readFile(DEFAULT_EQUALS), true),
        new TemplateResource("IntelliJ Default hashCode", readFile(DEFAULT_HASH_CODE), true),

        new TemplateResource("Equals/HashCodeBuilder (Apache commons-lang) equals", readFile(APACHE_EQUALS), true),
        new TemplateResource("Equals/HashCodeBuilder (Apache commons-lang) hashCode", readFile(APACHE_HASH_CODE), true),

        new TemplateResource("Equals/HashCodeBuilder (Apache commons-lang 3) equals", readFile(APACHE3_EQUALS), true),
        new TemplateResource("Equals/HashCodeBuilder (Apache commons-lang 3) hashCode", readFile(APACHE3_HASH_CODE), true),

        new TemplateResource("Objects.equal and hashCode (Guava) equals", readFile(GUAVA_EQUALS), true),
        new TemplateResource("Objects.equal and hashCode (Guava) hashCode", readFile(GUAVA_HASH_CODE), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  private static String readFile(String resourceName) throws IOException {
    return readFile(resourceName, EqualsHashCodeTemplatesManager.class);
  }

  public TemplateResource getDefaultEqualsTemplate() {
    return getDefaultTemplate(EQUALS_SUFFIX, HASH_CODE_SUFFIX);
  }

  public TemplateResource getDefaultHashcodeTemplate() {
    return getDefaultTemplate(HASH_CODE_SUFFIX, EQUALS_SUFFIX);
  }

  public String[] getTemplateNames() {
    final Set<String> names = new LinkedHashSet<String>();
    for (TemplateResource resource : getAllTemplates()) {
      names.add(getTemplateBaseName(resource));
    }
    return ArrayUtil.toStringArray(names);
  }

  @NotNull
  private String getTemplateBaseName(TemplateResource resource) {
    return StringUtil.trimEnd(StringUtil.trimEnd(resource.getFileName(), EQUALS_SUFFIX), HASH_CODE_SUFFIX).trim();
  }

  private TemplateResource getDefaultTemplate(String selfSuffix, String oppositeSuffix) {
    final TemplateResource defaultTemplate = getDefaultTemplate();
    final String fileName = defaultTemplate.getFileName();
    if (fileName.endsWith(selfSuffix)) {
      return defaultTemplate;
    }
    final String equalsTemplateName = StringUtil.trimEnd(fileName, oppositeSuffix) + selfSuffix;
    for (TemplateResource resource : getAllTemplates()) {
      if (equalsTemplateName.equals(resource.getFileName())) {
        return resource;
      }
    }
    assert false : selfSuffix + " template for " + fileName + " not found"; 
    return null;
  }

  public void setDefaultTemplate(String name) {
    Set<String> fullNames = ContainerUtil.newHashSet(name + " " + EQUALS_SUFFIX, name + " " + HASH_CODE_SUFFIX);
    for (TemplateResource resource : getAllTemplates()) {
      if (fullNames.contains(resource.getFileName())) {
        setDefaultTemplate(resource);
        break;
      }
    }
  }

  public String getDefaultTemplateBaseName() {
    return getTemplateBaseName(getDefaultTemplate());
  }
}
