package ru.compscicenter.edide;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: lia
 * Date: 10.05.14
 * Time: 12:45
 */
public class StudyFileEditorProvider implements FileEditorProvider {
    public static final String EDITOR_TYPE_ID = "StudyEditor";
    private FileEditorProvider defaultTextEditorProvider = TextEditorProvider.getInstance();
    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return defaultTextEditorProvider.accept(project, file);
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new StudyEditor(project, file);
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
      defaultTextEditorProvider.disposeEditor(editor);
    }

    @NotNull
    @Override
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return defaultTextEditorProvider.readState(sourceElement, project, file);
    }

    @Override
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
      defaultTextEditorProvider.writeState(state, project, targetElement);
    }

    @NotNull
    @Override
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
