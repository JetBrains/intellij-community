// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.template;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.element.FieldElement;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class TemplatesManager implements PersistentStateComponent<TemplatesState> {
  public static final Key<Map<String, PsiType>> TEMPLATE_IMPLICITS = Key.create("TEMPLATE_IMPLICITS");

  private TemplatesState myState = new TemplatesState();

  public abstract @NotNull List<TemplateResource> getDefaultTemplates();

  /**
   * Reads the content of the resource and return it as a String.
   * <p/>Uses the class loader that loaded this class to find the resource in its classpath.
   *
   * @param resource the resource name. Will lookup using the classpath.
   * @return the content if the resource
   * @throws IOException error reading the file.
   */
  protected static String readFile(String resource, Class<? extends TemplatesManager> templatesManagerClass) throws IOException {
    BufferedInputStream in = new BufferedInputStream(templatesManagerClass.getResourceAsStream(resource));
    return StringUtil.convertLineSeparators(FileUtil.loadTextAndClose(new InputStreamReader(in, StandardCharsets.UTF_8)));
  }

  @Override
  public @NotNull TemplatesState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull TemplatesState state) {
    if (StringUtil.isEmpty(state.defaultTemplateName) && !StringUtil.isEmpty(state.oldDefaultTemplateName)) {
      state.defaultTemplateName = state.oldDefaultTemplateName;
    }
    state.oldDefaultTemplateName = null;
    myState = state;
  }

  public void addTemplate(@NotNull TemplateResource template) {
    myState.templates.add(template);
  }

  public @NotNull Collection<TemplateResource> getAllTemplates() {
    Set<String> names = new HashSet<>();
    Collection<TemplateResource> templates = new LinkedHashSet<>(getDefaultTemplates());
    for (TemplateResource template : myState.templates) {
      if (names.add(template.getFileName())) {
        templates.add(template);
      }
    }
    return templates;
  }

  public TemplateResource getDefaultTemplate() {
    TemplateResource resource = findTemplateByName(myState.defaultTemplateName);
    if (resource != null) {
      return resource;
    }

    String initialTemplateName = getInitialTemplateName();
    resource = initialTemplateName != null ? findTemplateByName(initialTemplateName) : null;
    if (resource == null) {
      return getAllTemplates().iterator().next();
    }
    return resource;
  }

  protected String getInitialTemplateName() {
    return null;
  }

  public @Nullable TemplateResource findTemplateByName(String templateName) {
    for (TemplateResource template : getAllTemplates()) {
      if (Objects.equals(template.getFileName(), templateName)) {
        return template;
      }
    }

    return null;
  }

  public void setDefaultTemplate(@NotNull TemplateResource resource) {
    myState.defaultTemplateName = resource.getFileName();
  }

  public void setTemplates(@NotNull List<? extends TemplateResource> items) {
    myState.templates.clear();
    for (TemplateResource item : items) {
      if (!item.isDefault()) {
        myState.templates.add(item);
      }
    }
  }

  public static @NotNull PsiType createFieldListElementType(@NotNull Project project) {
    final PsiType classType = createElementType(project, FieldElement.class);
    PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(CommonClassNames.JAVA_UTIL_LIST,
                                                                        GlobalSearchScope.allScope(project));
    for (PsiClass listClass : classes) {
      if (listClass.getTypeParameters().length == 1) {
        return JavaPsiFacade.getElementFactory(project).createType(listClass, classType);
      }
    }
    return PsiTypes.nullType();
  }

  public static @NotNull PsiType createElementType(Project project, Class<?> elementClass) {
    final List<String> methodNames =
      ContainerUtil.mapNotNull(elementClass.getMethods(),
                               method -> {
                                 final String methodName = method.getName();
                                 if (methodName.startsWith("set") || method.isSynthetic() || method.isBridge()) {
                                   //hide setters from completion list
                                   return null;
                                 }
                                 String parametersString = StringUtil.join(method.getParameters(),
                                                                           param -> param.getParameterizedType().getTypeName() +
                                                                                    " " +
                                                                                    param.getName(),
                                                                           ", ");
                                 return method.getGenericReturnType().getTypeName() + " " + methodName + "(" + parametersString + ");";
                               });
    final String text = "interface " + elementClass.getSimpleName() + " {\n" + StringUtil.join(methodNames, "\n") + "}";
    final PsiClass aClass = JavaPsiFacade.getElementFactory(project).createClassFromText(text, null).getInnerClasses()[0];
    return JavaPsiFacade.getElementFactory(project).createType(aClass);
  }
}