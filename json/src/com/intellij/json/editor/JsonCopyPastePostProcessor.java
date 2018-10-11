// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

public class JsonCopyPastePostProcessor extends CopyPastePostProcessor<TextBlockTransferableData> {
  static class DumbData implements TextBlockTransferableData {
    private final DataFlavor DATA_FLAVOR = new DataFlavor(JsonCopyPastePostProcessor.class, "class: JsonCopyPastePostProcessor");
    @Override
    public DataFlavor getFlavor()  {
      return  DATA_FLAVOR;
    }

    @Override
    public int getOffsetCount() {
      return 0;
    }

    @Override
    public int getOffsets(int[] offsets, int index) {
      return index;
    }

    @Override
    public int setOffsets(int[] offsets, int index) {
      return index;
    }
  }

  @NotNull
  @Override
  public List<TextBlockTransferableData> collectTransferableData(PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
    return ContainerUtil.emptyList();
  }

  @NotNull
  @Override
  public List<TextBlockTransferableData> extractTransferableData(Transferable content) {
    try {
      return Collections.singletonList(new DumbData());
    }
    catch (Exception e) {
      return ContainerUtil.emptyList();
    }
  }

  @Override
  public void processTransferableData(Project project,
                                      Editor editor,
                                      RangeMarker bounds,
                                      int caretOffset,
                                      Ref<Boolean> indented,
                                      List<TextBlockTransferableData> values) {
    if (!JsonEditorOptions.getInstance().COMMA_ON_PASTE) return;
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    manager.commitDocument(editor.getDocument());
    final PsiFile psiFile = manager.getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    fixTrailingComma(bounds, psiFile, manager);
    fixLeadingComma(bounds, psiFile, manager);
  }

  private static void fixLeadingComma(@NotNull RangeMarker bounds, @NotNull PsiFile psiFile, @NotNull PsiDocumentManager manager) {
    final PsiElement startElement = skipWhitespaces(psiFile.findElementAt(bounds.getStartOffset()));
    PsiElement propertyOrArrayItem = getParentPropertyOrArrayItem(startElement);

    if (propertyOrArrayItem == null) return;

    PsiElement prevSibling = propertyOrArrayItem.getPrevSibling();
    while (prevSibling instanceof PsiWhiteSpace) {
      prevSibling = prevSibling.getPrevSibling();
    }
    if (prevSibling instanceof PsiErrorElement) {
      final int offset = prevSibling.getTextRange().getEndOffset();
      ApplicationManager.getApplication().runWriteAction(() -> bounds.getDocument().insertString(offset, ","));
      manager.commitDocument(bounds.getDocument());
    }
  }

  @Nullable
  private static PsiElement getParentPropertyOrArrayItem(@Nullable PsiElement startElement) {
    PsiElement propertyOrArrayItem = PsiTreeUtil.getParentOfType(startElement, JsonProperty.class, JsonArray.class);
    if (propertyOrArrayItem instanceof JsonArray) {
      JsonValue match = null;
      for (JsonValue value : ((JsonArray)propertyOrArrayItem).getValueList()) {
        if (PsiTreeUtil.isAncestor(value, startElement, false)) {
          match = value;
          break;
        }
      }
      propertyOrArrayItem = match;
    }
    return propertyOrArrayItem;
  }

  private static void fixTrailingComma(@NotNull RangeMarker bounds, @NotNull PsiFile psiFile, @NotNull PsiDocumentManager manager) {
    final PsiElement endElement = skipWhitespaces(psiFile.findElementAt(bounds.getEndOffset() - 1));

    if (endElement instanceof LeafPsiElement && ((LeafPsiElement)endElement).getElementType() == JsonElementTypes.COMMA) {
      final PsiElement nextNext = skipWhitespaces(endElement.getNextSibling());
      if (nextNext instanceof LeafPsiElement && (((LeafPsiElement)nextNext).getElementType() == JsonElementTypes.R_CURLY ||
                                                  ((LeafPsiElement)nextNext).getElementType() == JsonElementTypes.R_BRACKET)) {
        ApplicationManager.getApplication().runWriteAction(() -> endElement.delete());
      }
    }
    else {
      final PsiElement property = getParentPropertyOrArrayItem(endElement);
      if (property != null && skipWhitespaces(property.getNextSibling()) instanceof PsiErrorElement) {
        ApplicationManager.getApplication().runWriteAction(() -> bounds.getDocument().insertString(property.getTextRange().getEndOffset(), ","));
        manager.commitDocument(bounds.getDocument());
      }
    }
  }

  private static PsiElement skipWhitespaces(@Nullable PsiElement element) {
    while (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    return element;
  }
}
