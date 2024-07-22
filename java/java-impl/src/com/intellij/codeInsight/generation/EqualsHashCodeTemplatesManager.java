// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;

import java.io.IOException;
import java.util.*;

@State(name = "EqualsHashCodeTemplates", storages = @Storage("equalsHashCodeTemplates.xml"), category = SettingsCategory.CODE)
public final class EqualsHashCodeTemplatesManager extends EqualsHashCodeTemplatesManagerBase {

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

  public static final @NonNls String EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG = "Apache Commons Lang EqualsBuilder and HashCodeBuilder";
  public static final @NonNls String EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3 = "Apache Commons Lang 3 EqualsBuilder and HashCodeBuilder";
  public static final @NonNls String OBJECTS_EQUAL_AND_HASH_CODE_GUAVA = "Guava Objects.equal() and hashCode()";
  public static final @NonNls String JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE = "java.util.Objects.equals() and hash() (Java 7 and higher)";

  public static EqualsHashCodeTemplatesManager getInstance() {
    return ApplicationManager.getApplication().getService(EqualsHashCodeTemplatesManager.class);
  }

  @Override
  public @NotNull List<TemplateResource> getDefaultTemplates() {
    try {
      return Arrays.asList(new TemplateResource(toEqualsName(INTELLI_J_DEFAULT), readFile(DEFAULT_EQUALS), true),
                           new TemplateResource(toHashCodeName(INTELLI_J_DEFAULT), readFile(DEFAULT_HASH_CODE), true),

                           new TemplateResource(toEqualsName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG), readFile(APACHE_EQUALS), true, APACHE_BUILDER),
                           new TemplateResource(toHashCodeName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG), readFile(APACHE_HASH_CODE), true,
                                                APACHE_BUILDER),

                           new TemplateResource(toEqualsName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3), readFile(APACHE3_EQUALS), true, APACHE3_BUILDER),
                           new TemplateResource(toHashCodeName(EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3), readFile(APACHE3_HASH_CODE), true,
                                                APACHE3_BUILDER),

                           new TemplateResource(toEqualsName(OBJECTS_EQUAL_AND_HASH_CODE_GUAVA), readFile(GUAVA_EQUALS), true, GUAVA_BUILDER),
                           new TemplateResource(toHashCodeName(OBJECTS_EQUAL_AND_HASH_CODE_GUAVA), readFile(GUAVA_HASH_CODE), true, GUAVA_BUILDER),

                           new TemplateResource(toEqualsName(JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE), readFile(OBJECTS_EQUALS), true, OBJECTS_BUILDER),
                           new TemplateResource(toHashCodeName(JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE), readFile(OBJECTS_HASH_CODE), true, OBJECTS_BUILDER));
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  @Override
  protected String getInitialTemplateName() {
    return toEqualsName(JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }
}
