package com.intellij.json.navigation;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

/**
 * @author Mikhail Golubev
 */
public class JsonQualifiedNameProvider implements QualifiedNameProvider {
  @Nullable
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedName(PsiElement element) {
    if (!(element instanceof JsonElement)) {
      return null;
    }
    final LinkedList<String> qualifiers = new LinkedList<>();
    JsonProperty parentProperty = PsiTreeUtil.getNonStrictParentOfType(element, JsonProperty.class);
    while (parentProperty != null) {
      qualifiers.addFirst(parentProperty.getName());
      parentProperty = PsiTreeUtil.getParentOfType(parentProperty, JsonProperty.class);
    }
    return qualifiers.isEmpty() ? null : StringUtil.join(qualifiers, ".");
  }

  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    return null;
  }

  @Override
  public void insertQualifiedName(String fqn, PsiElement element, Editor editor, Project project) {
    EditorModificationUtil.insertStringAtCaret(editor, fqn);
  }
}
