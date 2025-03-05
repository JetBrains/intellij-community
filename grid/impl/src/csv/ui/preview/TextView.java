package com.intellij.database.csv.ui.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TextView {
  private final Editor myEditor;

  public TextView(@NotNull Disposable parent) {
    this(EditorFactory.getInstance().createDocument(""), parent);
  }

  public TextView(@NotNull Document document, @NotNull Disposable parent) {
    myEditor = EditorFactory.getInstance().createEditor(document);
    myEditor.getSettings().setRightMarginShown(false);
    myEditor.getSettings().setLineNumbersShown(true);
    myEditor.getSettings().setLineMarkerAreaShown(false);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }
    });
  }

  public @NotNull JComponent getComponent() {
    return myEditor.getComponent();
  }

  public void setText(final @NotNull String text) {
    ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(StringUtilRt.convertLineSeparators(text)));
  }
}
