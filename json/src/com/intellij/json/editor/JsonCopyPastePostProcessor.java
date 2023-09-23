// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
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

public final class JsonCopyPastePostProcessor extends CopyPastePostProcessor<TextBlockTransferableData> {
  static final List<TextBlockTransferableData> DATA_LIST = Collections.singletonList(new DumbData());
  static final class DumbData implements TextBlockTransferableData {
    private static final DataFlavor DATA_FLAVOR = new DataFlavor(JsonCopyPastePostProcessor.class, "class: JsonCopyPastePostProcessor");
    @Override
    public @Nullable DataFlavor getFlavor()  {
      return  DATA_FLAVOR;
    }
  }

  @Override
  public @NotNull List<TextBlockTransferableData> collectTransferableData(@NotNull PsiFile file, @NotNull Editor editor, int @NotNull [] startOffsets, int @NotNull [] endOffsets) {
    return ContainerUtil.emptyList();
  }

  @Override
  public @NotNull List<TextBlockTransferableData> extractTransferableData(@NotNull Transferable content) {
    // if this list is empty, processTransferableData won't be called
    return DATA_LIST;
  }

  @Override
  public void processTransferableData(@NotNull Project project,
                                      @NotNull Editor editor,
                                      @NotNull RangeMarker bounds,
                                      int caretOffset,
                                      @NotNull Ref<? super Boolean> indented,
                                      @NotNull List<? extends TextBlockTransferableData> values) {
    fixCommasOnPaste(project, editor, bounds);
  }

  private static void fixCommasOnPaste(@NotNull Project project, @NotNull Editor editor, @NotNull RangeMarker bounds) {
    if (!JsonEditorOptions.getInstance().COMMA_ON_PASTE) return;

    if (!isJsonEditor(project, editor)) return;

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    manager.commitDocument(editor.getDocument());
    final PsiFile psiFile = manager.getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    fixTrailingComma(bounds, psiFile, manager);
    fixLeadingComma(bounds, psiFile, manager);
  }

  private static boolean isJsonEditor(@NotNull Project project,
                                      @NotNull Editor editor) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) return false;
    final FileType fileType = file.getFileType();
    if (fileType instanceof JsonFileType) return true;
    if (!ScratchUtil.isScratch(file)) return false;
    return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) instanceof JsonFile;
  }

  private static void fixLeadingComma(@NotNull RangeMarker bounds, @NotNull PsiFile psiFile, @NotNull PsiDocumentManager manager) {
    final PsiElement startElement = skipWhitespaces(psiFile.findElementAt(bounds.getStartOffset()));
    PsiElement propertyOrArrayItem = startElement instanceof JsonProperty ? startElement : getParentPropertyOrArrayItem(startElement);

    if (propertyOrArrayItem == null) return;

    PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(propertyOrArrayItem);
    if (prevSibling instanceof PsiErrorElement) {
      final int offset = prevSibling.getTextRange().getEndOffset();
      ApplicationManager.getApplication().runWriteAction(() -> bounds.getDocument().insertString(offset, ","));
      manager.commitDocument(bounds.getDocument());
    }
  }

  private static @Nullable PsiElement getParentPropertyOrArrayItem(@Nullable PsiElement startElement) {
    PsiElement propertyOrArrayItem = PsiTreeUtil.getParentOfType(startElement, JsonProperty.class, JsonArray.class);
    if (propertyOrArrayItem instanceof JsonArray) {
      for (JsonValue value : ((JsonArray)propertyOrArrayItem).getValueList()) {
        if (PsiTreeUtil.isAncestor(value, startElement, false)) {
          return value;
        }
      }
      return null;
    }
    return propertyOrArrayItem;
  }

  private static void fixTrailingComma(@NotNull RangeMarker bounds, @NotNull PsiFile psiFile, @NotNull PsiDocumentManager manager) {
    PsiElement endElement = skipWhitespaces(psiFile.findElementAt(bounds.getEndOffset() - 1));
    if (endElement != null && endElement.getTextOffset() >= bounds.getEndOffset()) {
      endElement = PsiTreeUtil.skipWhitespacesBackward(endElement);
    }

    if (endElement instanceof LeafPsiElement && ((LeafPsiElement)endElement).getElementType() == JsonElementTypes.COMMA) {
      final PsiElement nextNext = skipWhitespaces(endElement.getNextSibling());
      if (nextNext instanceof LeafPsiElement && (((LeafPsiElement)nextNext).getElementType() == JsonElementTypes.R_CURLY ||
                                                  ((LeafPsiElement)nextNext).getElementType() == JsonElementTypes.R_BRACKET)) {
        PsiElement finalEndElement = endElement;
        ApplicationManager.getApplication().runWriteAction(() -> finalEndElement.delete());
      }
    }
    else {
      final PsiElement property = getParentPropertyOrArrayItem(endElement);
      if (endElement instanceof PsiErrorElement || property != null && skipWhitespaces(property.getNextSibling()) instanceof PsiErrorElement) {
        PsiElement finalEndElement1 = endElement;
        ApplicationManager.getApplication().runWriteAction(() -> bounds.getDocument().insertString(getOffset(property, finalEndElement1), ","));
        manager.commitDocument(bounds.getDocument());
      }
    }
  }

  private static int getOffset(@Nullable PsiElement property, @Nullable PsiElement finalEndElement1) {
    if (finalEndElement1 instanceof PsiErrorElement) return finalEndElement1.getTextOffset();
    assert finalEndElement1 != null;
    return property != null ? property.getTextRange().getEndOffset() : finalEndElement1.getTextOffset();
  }

  private static @Nullable PsiElement skipWhitespaces(@Nullable PsiElement element) {
    while (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    return element;
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
