package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class JsonPsiImplUtils {
  @NotNull
  public static String getName(@NotNull JsonProperty property) {
    return StringUtil.unquoteString(property.getNameElement().getText());
  }

  public static boolean isQuotedString(@NotNull JsonLiteral literal) {
    return literal.getNode().findChildByType(JsonElementTypes.STRING) != null;
  }

  public static void delete(@NotNull JsonProperty property) {
    final ASTNode myNode = property.getNode();
    JsonPsiChangeUtils.removeCommaSeparatedFromList(myNode, myNode.getTreeParent());
  }

  @Nullable
  public static JsonProperty findProperty(@NotNull JsonObject object, @NotNull String name) {
    Collection<JsonProperty> properties = PsiTreeUtil.findChildrenOfType(object, JsonProperty.class);
    for (JsonProperty property : properties) {
      if (property.getName().equals(name)) {
        return property;
      }
    }
    return null;
  }
}
