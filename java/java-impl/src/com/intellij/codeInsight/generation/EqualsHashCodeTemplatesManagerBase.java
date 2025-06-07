// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.generation.GenerateEqualsHelper.INSTANCE_NAME;

public abstract class EqualsHashCodeTemplatesManagerBase extends TemplatesManager {
  public static final @NonNls String INTELLI_J_DEFAULT = "IntelliJ Default";
  protected static final String DEFAULT_EQUALS = "/com/intellij/codeInsight/generation/defaultEquals.vm";
  protected static final String DEFAULT_HASH_CODE = "/com/intellij/codeInsight/generation/defaultHashCode.vm";
  private static final String EQUALS_SUFFIX = "equals";
  private static final String HASH_CODE_SUFFIX = "hashCode";

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
    Set<String> fullNames = ContainerUtil.newHashSet(toEqualsName(name),
                                                     toHashCodeName(name));
    for (TemplateResource resource : getAllTemplates()) {
      if (fullNames.contains(resource.getFileName())) {
        setDefaultTemplate(resource);
        break;
      }
    }
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

  public @NlsSafe String getDefaultTemplateBaseName() {
    return getTemplateBaseName(getDefaultTemplate());
  }

  public Map<String, PsiType> getEqualsImplicitVars(Project project) {
    Map<String, PsiType> map = GenerateEqualsHelper.getEqualsImplicitVars(project);
    map.put(INSTANCE_NAME, PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project)));
    appendDefaultJavaSettings(project, map);
    return map;
  }

  private static void appendDefaultJavaSettings(Project project, Map<String, PsiType> map) {
    map.put("java_version", PsiTypes.intType());
    map.put("settings", createElementType(project, JavaCodeStyleSettings.class));
  }

  public Map<String, PsiType> getHashCodeImplicitVars(Project project) {
    Map<String, PsiType> map = GenerateEqualsHelper.getHashCodeImplicitVars();
    appendDefaultJavaSettings(project, map);
    return map;
  }

  protected static String readFile(String resourceName) throws IOException {
    return readFile(resourceName, EqualsHashCodeTemplatesManager.class);
  }

  public static @NotNull @NlsSafe String getTemplateBaseName(TemplateResource resource) {
    return StringUtil.trimEnd(StringUtil.trimEnd(resource.getFileName(), EQUALS_SUFFIX), HASH_CODE_SUFFIX).trim();
  }

  public static String toEqualsName(String name) {
    return name + " " + EQUALS_SUFFIX;
  }

  public static String toHashCodeName(String name) {
    return name + " " + HASH_CODE_SUFFIX;
  }
}
