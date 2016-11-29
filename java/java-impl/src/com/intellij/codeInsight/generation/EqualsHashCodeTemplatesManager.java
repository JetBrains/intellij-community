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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;

@State(name = "EqualsHashCodeTemplates", storages = @Storage("equalsHashCodeTemplates.xml"))
public class EqualsHashCodeTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_EQUALS = "/com/intellij/codeInsight/generation/defaultEquals.vm";
  private static final String DEFAULT_HASH_CODE = "/com/intellij/codeInsight/generation/defaultHashCode.vm";

  private static final String APACHE3_EQUALS = "/com/intellij/codeInsight/generation/apacheEqualsBuilder3.vm";
  private static final String APACHE3_HASH_CODE = "/com/intellij/codeInsight/generation/apacheHashCodeBuilder3.vm";
  private static final String APACHE3_BUILDER = "org.apache.commons.lang3.builder.EqualsBuilder";

  private static final String APACHE_EQUALS = "/com/intellij/codeInsight/generation/apacheEqualsBuilder.vm";
  private static final String APACHE_HASH_CODE = "/com/intellij/codeInsight/generation/apacheHashCodeBuilder.vm";
  private static final String APACHE_BUILDER = "org.apache.commons.lang.builder.EqualsBuilder";
  

  private static final String GUAVA_EQUALS = "/com/intellij/codeInsight/generation/guavaEquals.vm";
  private static final String GUAVA_HASH_CODE = "/com/intellij/codeInsight/generation/guavaHashCode.vm";
  private static final String GUAVA_BUILDER = "com.google.common.base.Objects";
  
  private static final String OBJECTS_EQUALS = "/com/intellij/codeInsight/generation/objectsEquals.vm";
  private static final String OBJECTS_HASH_CODE = "/com/intellij/codeInsight/generation/objectsHashCode.vm";
  private static final String OBJECTS_BUILDER = "java.util.Objects";

  private static final String EQUALS_SUFFIX = "equals";
  private static final String HASH_CODE_SUFFIX = "hashCode";

  @NonNls public static final String INTELLI_J_DEFAULT = "IntelliJ Default";
  @NonNls public static final String EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG = "Equals/HashCodeBuilder (Apache commons-lang)";
  @NonNls public static final String EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3 = "Equals/HashCodeBuilder (Apache commons-lang 3)";
  @NonNls public static final String OBJECTS_EQUAL_AND_HASH_CODE_GUAVA = "Objects.equal and hashCode (Guava)";
  @NonNls public static final String JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE = "java.util.Objects.equals and hashCode (java 7+)";


  public static EqualsHashCodeTemplatesManager getInstance() {
    return ServiceManager.getService(EqualsHashCodeTemplatesManager.class);
  }

  @Override
  public TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[] {
        new TemplateResource(toEqualsName(INTELLI_J_DEFAULT), readFile(DEFAULT_EQUALS), true),
        new TemplateResource(toHashCodeName(INTELLI_J_DEFAULT), readFile(DEFAULT_HASH_CODE), true),

        new TemplateResource(toEqualsName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG), readFile(APACHE_EQUALS), true, APACHE_BUILDER),
        new TemplateResource(toHashCodeName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG), readFile(APACHE_HASH_CODE), true, APACHE_BUILDER),

        new TemplateResource(toEqualsName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3), readFile(APACHE3_EQUALS), true, APACHE3_BUILDER),
        new TemplateResource(toHashCodeName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3), readFile(APACHE3_HASH_CODE), true, APACHE3_BUILDER),

        new TemplateResource(toEqualsName(OBJECTS_EQUAL_AND_HASH_CODE_GUAVA), readFile(GUAVA_EQUALS), true, GUAVA_BUILDER),
        new TemplateResource(toHashCodeName(OBJECTS_EQUAL_AND_HASH_CODE_GUAVA), readFile(GUAVA_HASH_CODE), true, GUAVA_BUILDER),

        new TemplateResource(toEqualsName(JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE), readFile(OBJECTS_EQUALS), true, OBJECTS_BUILDER),
        new TemplateResource(toHashCodeName(JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE), readFile(OBJECTS_HASH_CODE), true, OBJECTS_BUILDER)
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
    return getEqualsTemplate(getDefaultTemplate());
  }

  public TemplateResource getDefaultHashcodeTemplate() {
    return getHashcodeTemplate(getDefaultTemplate());
  }

  public TemplateResource getEqualsTemplate(TemplateResource template) {
    return getDefaultTemplate(EQUALS_SUFFIX, HASH_CODE_SUFFIX, template);
  }

  public TemplateResource getHashcodeTemplate(TemplateResource template) {
    return getDefaultTemplate(HASH_CODE_SUFFIX, EQUALS_SUFFIX, template);
  }

  @NotNull
  public static String getTemplateBaseName(TemplateResource resource) {
    return StringUtil.trimEnd(StringUtil.trimEnd(resource.getFileName(), EQUALS_SUFFIX), HASH_CODE_SUFFIX).trim();
  }

  private TemplateResource getDefaultTemplate(String selfSuffix, String oppositeSuffix, TemplateResource defaultTemplate) {
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
    Set<String> fullNames = ContainerUtil.newHashSet(toEqualsName(name), toHashCodeName(name));
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

  public static String toEqualsName(String name) {
    return name + " " + EQUALS_SUFFIX;
  }
  
  public static String toHashCodeName(String name) {
    return name + " " + HASH_CODE_SUFFIX;
  }

  public Collection<Couple<TemplateResource>> getTemplateCouples() {
    final LinkedHashMap<String, Couple<TemplateResource>> resources = new LinkedHashMap<>();
    for (TemplateResource resource : getAllTemplates()) {
      final String baseName = getTemplateBaseName(resource);
      TemplateResource eq = toEqualsName(baseName).equals(resource.getFileName()) ? resource : null;
      TemplateResource hc = toHashCodeName(baseName).equals(resource.getFileName()) ? resource : null;
      final Couple<TemplateResource> couple = resources.get(baseName);
      if (couple != null) {
        resources.put(baseName, Couple.of(couple.first != null ? couple.first : eq,
                                          couple.second != null ? couple.second : hc));
      }
      else {
        resources.put(baseName, Couple.of(eq, hc));
      }
    }
    return resources.values();
  }
}
