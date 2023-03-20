package com.intellij.json.navigation;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.json.JsonUtil;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonQualifiedNameProvider implements QualifiedNameProvider {
  @Nullable
  @Override
  public PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedName(@NotNull PsiElement element) {
    return generateQualifiedName(element, JsonQualifiedNameKind.Qualified);
  }

  public static String generateQualifiedName(PsiElement element, JsonQualifiedNameKind qualifiedNameKind) {
    if (!(element instanceof JsonElement)) {
      return null;
    }
    JsonElement parentProperty = PsiTreeUtil.getNonStrictParentOfType(element, JsonProperty.class, JsonArray.class);
    StringBuilder builder = new StringBuilder();
    while (parentProperty != null) {
      if (parentProperty instanceof JsonProperty jsonProperty) {
        String name = jsonProperty.getName();
        if (qualifiedNameKind == JsonQualifiedNameKind.JsonPointer) {
          name = JsonPointerUtil.escapeForJsonPointer(name);
        }
        builder.insert(0, name);
        builder.insert(0, qualifiedNameKind == JsonQualifiedNameKind.JsonPointer ? "/" : ".");
      }
      else {
        int index = JsonUtil.getArrayIndexOfItem(element instanceof JsonProperty ? element.getParent() : element);
        if (index == -1) return null;
        builder.insert(0, qualifiedNameKind == JsonQualifiedNameKind.JsonPointer ? ("/" + index) : ("[" + index + "]"));
      }
      element = parentProperty;
      parentProperty = PsiTreeUtil.getParentOfType(parentProperty, JsonProperty.class, JsonArray.class);
    }

    if (builder.length() == 0) return null;

    // if the first operation is array indexing, we insert the 'root' element $
    if (builder.charAt(0) == '[') {
      builder.insert(0, "$");
    }

    return StringUtil.trimStart(builder.toString(), ".");
  }

  @Override
  public PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    return null;
  }
}
