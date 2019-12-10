// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class JsonSortPropertiesIntention implements IntentionAction, LowPriorityAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return JsonBundle.message("json.intention.sort.properties");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JsonBundle.message("json.intention.sort.properties");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement parent = findParentObject(editor, file);
    return parent instanceof JsonObject && !isSorted((JsonObject)parent);
  }

  @Nullable
  private static PsiElement findParentObject(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class);
    return property == null ? null : property.getParent();
  }

  private static boolean isSorted(@NotNull JsonObject parent) {
    List<JsonProperty> list = parent.getPropertyList();
    if (list.size() <= 1) return true;
    List<String> names = ContainerUtil.map(list, p -> p.getName());
    return ContainerUtil.equalsIdentity(ContainerUtil.sorted(names), names);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement parentObject = findParentObject(editor, file);
    assert parentObject instanceof JsonObject;
    // cycle-sort performs the minimal amount of modifications, and we want to patch the tree as little as possible
    cycleSortProperties(((JsonObject)parentObject).getPropertyList());
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(parentObject);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void cycleSortProperties(@NotNull List<JsonProperty> properties) {
    int size = properties.size();
    for (int cycleStart = 0; cycleStart < size; cycleStart++) {
      JsonProperty item = properties.get(cycleStart);
      int pos = advance(properties, size, cycleStart, item);
      if (pos == -1) continue;
      if (pos != cycleStart) {
        exchange(properties, pos, cycleStart);
      }
      while (pos != cycleStart) {
        pos = advance(properties, size, cycleStart, properties.get(cycleStart));
        if (pos == -1) break;
        if (pos != cycleStart) {
          exchange(properties, pos, cycleStart);
        }
      }
    }
  }

  private static int advance(@NotNull List<JsonProperty> properties, int size, int cycleStart, JsonProperty item) {
    int pos = cycleStart;
    String itemName = item.getName();
    for (int i = cycleStart + 1; i < size; i++) {
      if (properties.get(i).getName().compareTo(itemName) < 0) pos++;
    }
    if (pos == cycleStart) return -1;
    while (Objects.equals(itemName, properties.get(pos).getName())) pos++;
    return pos;
  }

  private static void exchange(@NotNull List<JsonProperty> properties, int pos, int item) {
    JsonProperty propertyAtPos = properties.get(pos);
    JsonProperty itemProperty = properties.get(item);
    properties.set(pos, (JsonProperty)propertyAtPos.getParent().addBefore(itemProperty, propertyAtPos));
    properties.set(item, (JsonProperty)itemProperty.getParent().addBefore(propertyAtPos, itemProperty));
    propertyAtPos.delete();
    itemProperty.delete();
  }
}
