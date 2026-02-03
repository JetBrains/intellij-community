// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.template.toString;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@State(name = "ToStringTemplates", storages = @Storage("toStringTemplates.xml"), category = SettingsCategory.CODE)
public final class ToStringTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_CONCAT = "DefaultConcatMember.vm";
  private static final String DEFAULT_CONCAT_GROOVY = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberGroovy.vm";
  private static final String DEFAULT_CONCAT_SUPER = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberSuper.vm";
  private static final String DEFAULT_CONCAT_SUPER_GROOVY = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberSuperGroovy.vm";
  private static final String DEFAULT_BUFFER = "/org/jetbrains/java/generate/template/toString/DefaultBuffer.vm";
  private static final String DEFAULT_BUILDER = "/org/jetbrains/java/generate/template/toString/DefaultBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER = "/org/jetbrains/java/generate/template/toString/DefaultToStringBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER3 = "/org/jetbrains/java/generate/template/toString/DefaultToStringBuilder3.vm";
  private static final String DEFAULT_GUAVA = "/org/jetbrains/java/generate/template/toString/DefaultGuava.vm";
  private static final String DEFAULT_GUAVA_18 = "/org/jetbrains/java/generate/template/toString/DefaultGuava18.vm";
  private static final String DEFAULT_STRING_JOINER = "/org/jetbrains/java/generate/template/toString/StringJoiner.vm";

  public static TemplatesManager getInstance() {
    return ApplicationManager.getApplication().getService(ToStringTemplatesManager.class);
  }

  @Override
  public @NotNull List<TemplateResource> getDefaultTemplates() {
    try {
      return Arrays.asList(new TemplateResource("String concat (+)", readFile(DEFAULT_CONCAT), true),
                           new TemplateResource("String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER), true),
                           new TemplateResource("StringBuffer", readFile(DEFAULT_BUFFER), true),
                           new TemplateResource("StringBuilder (JDK 1.5)", readFile(DEFAULT_BUILDER), true),
                           new TemplateResource("ToStringBuilder (Apache commons-lang)", readFile(DEFAULT_TOSTRINGBUILDER), true, "org.apache.commons.lang.builder.ToStringBuilder"),
                           new TemplateResource("ToStringBuilder (Apache commons-lang 3)", readFile(DEFAULT_TOSTRINGBUILDER3), true, "org.apache.commons.lang3.builder.ToStringBuilder"),
                           new TemplateResource("Objects.toStringHelper (Guava)", readFile(DEFAULT_GUAVA), true, "com.google.common.base.Objects"),
                           new TemplateResource("MoreObjects.toStringHelper (Guava 18+)", readFile(DEFAULT_GUAVA_18), true, "com.google.common.base.MoreObjects"),
                           new TemplateResource("StringJoiner (JDK 1.8)", readFile(DEFAULT_STRING_JOINER), true),
                           new TemplateResource("Groovy: String concat (+)", readFile(DEFAULT_CONCAT_GROOVY), true),
                           new TemplateResource("Groovy: String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER_GROOVY), true));
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  private static String readFile(String resource) throws IOException {
    return readFile(resource, ToStringTemplatesManager.class);
  }
}
