package com.intellij.json.breadcrumbs;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonUtil;
import com.intellij.json.navigation.JsonQualifiedNameKind;
import com.intellij.json.navigation.JsonQualifiedNameProvider;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.Language;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonBreadcrumbsProvider implements BreadcrumbsProvider {
  private static final Language[] LANGUAGES = new Language[]{JsonLanguage.INSTANCE};

  @Override
  public Language[] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof JsonProperty || JsonUtil.isArrayElement(e);
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof JsonProperty) {
      return ((JsonProperty)e).getName();
    }
    else if (JsonUtil.isArrayElement(e)) {
      int i = JsonUtil.getArrayIndexOfItem(e);
      if (i != -1) return String.valueOf(i);
    }
    throw new AssertionError("Breadcrumbs can be extracted only from JsonProperty elements or JsonArray child items");
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    return JsonSchemaDocumentationProvider.findSchemaAndGenerateDoc(e, null, true);
  }

  @NotNull
  @Override
  public List<? extends Action> getContextActions(@NotNull PsiElement element) {
    JsonQualifiedNameKind[] values = JsonQualifiedNameKind.values();
    List<Action> actions = ContainerUtil.newArrayListWithCapacity(values.length);
    for (JsonQualifiedNameKind kind: values) {
      actions.add(new AbstractAction(JsonBundle.message("json.copy.to.clipboard", kind.toString())) {
        @Override
        public void actionPerformed(ActionEvent e) {
          CopyPasteManager.getInstance().setContents(new StringSelection(JsonQualifiedNameProvider.generateQualifiedName(element, kind)));
        }
      });
    }
    return actions;
  }
}
