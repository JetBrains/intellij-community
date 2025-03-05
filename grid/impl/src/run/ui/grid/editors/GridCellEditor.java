package com.intellij.database.run.ui.grid.editors;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface GridCellEditor extends Disposable {
  @NotNull
  JComponent getComponent();

  default boolean shouldMoveFocus() {
    return true;
  }

  @Nullable
  Object getValue();

  @NotNull
  String getText();

  /**
   * This method is called by a grid which uses this editor, when editing stops. <br/>
   * If you want to stop editing, call {@link com.intellij.database.datagrid.CoreGrid#stopEditing()}.
   *
   * @return true, if editing can be stopped, false otherwise.
   */
  boolean stop();

  /**
   * This method is called by a grid which uses this editor, when editing cancels. <br/>
   * If you want to cancel editing, call {@link com.intellij.database.datagrid.CoreGrid#cancelEditing()}
   */
  void cancel();

  boolean isColumnSpanAllowed();

  void setEditingListener(@NotNull Consumer<Object> listener);

  abstract class Adapter implements GridCellEditor {

    private Consumer<Object> myEditingListener;

    @Override
    public boolean stop() {
      return true;
    }

    @Override
    public void cancel() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean isColumnSpanAllowed() {
      return true;
    }

    @Override
    public void setEditingListener(@NotNull Consumer<Object> listener) {
      myEditingListener = listener;
    }

    protected final void fireEditing(@Nullable Object object) {
      if (myEditingListener != null) myEditingListener.consume(object);
    }
  }

  interface EditorBased {
    @Nullable Editor getEditor();
  }
}
