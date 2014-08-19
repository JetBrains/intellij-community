package com.intellij.json.breadcrumbs;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonBreadcrumbsProvider extends BreadcrumbsInfoProvider {
  private static final Language[] LANGUAGES = new Language[]{JsonLanguage.INSTANCE};

  @Override
  public Language[] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return isProperty(e) || isArrayElement(e);
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    if (isProperty(e)) {
      return ((JsonProperty)e).getName();
    }
    else if (isArrayElement(e)) {
      List<JsonValue> elements = ((JsonArray)e.getParent()).getValueList();
      for (int i = 0; i < elements.size(); i++) {
        if (e == elements.get(i)) {
          return String.valueOf(i);
        }
      }
    }
    throw new AssertionError("Breadcrumbs can be taken only for JsonProperty or JsonArray's element");
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    return null;
  }

  private static boolean isProperty(PsiElement element) {
    return element instanceof JsonProperty;
  }

  private static boolean isArrayElement(PsiElement element) {
    return element instanceof JsonValue && element.getParent() instanceof JsonArray;
  }
}
