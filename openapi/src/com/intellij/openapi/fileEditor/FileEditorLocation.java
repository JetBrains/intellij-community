package com.intellij.openapi.fileEditor;

/**
 * This interface specifies a location in its file editor.
 * The Comparable interface implementation should present some natural order on locations.
 * Usually it's top-to-bottom & left-to-right order.
 *
 * The locations from different editors are
 * not expected to be compared together.
 */
public interface FileEditorLocation extends Comparable<FileEditorLocation> {
  FileEditor getEditor();
}
