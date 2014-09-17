package com.intellij.json.breadcrumbs;

import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import com.intellij.json.psi.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return e instanceof JsonProperty;
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof JsonProperty) {
      return ((JsonProperty)e).getName();
    }
    //else if (isArrayElement(e)) {
    //  List<JsonValue> elements = ((JsonArray)e.getParent()).getValueList();
    //  for (int i = 0; i < elements.size(); i++) {
    //    if (e == elements.get(i)) {
    //      return String.valueOf(i);
    //    }
    //  }
    //}
    throw new AssertionError("Breadcrumbs can be extracted only from JsonProperty elements");
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    return null;
  }
}
