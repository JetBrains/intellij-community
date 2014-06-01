package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.jetbrains.python.documentation.PyTypeModelBuilder;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * User: lia
 * Date: 24.05.14
 * Time: 22:13
 */
public class StudyDocumentationProvider extends DocumentationProviderEx {

    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return "Study docs";
    }

    @Nullable
    @Override
    public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
        return null;
    }



    @Nullable
    @Override
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        String file = element.getContainingFile().getName();

        FileEditor[] fe = FileEditorManager.getInstance(element.getProject()).getAllEditors();
        LogicalPosition pos = ((PsiAwareTextEditorImpl)((StudyEditor) fe[0]).getDefaultEditor()).getEditor().getCaretModel().getLogicalPosition();
        //((PsiAwareTextEditorImpl) ((StudyEditor) fe[0]).defaultEditor).a().getCaretModel().getLogicalPosition()
        //Editor selectedTextEditor = FileEditorManager.getInstance(element.getProject()).getSelectedTextEditor();
        //Document curDoc = selectedTextEditor.getDocument();
        //VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(curDoc);
        //element.getProject().
        TaskManager tm =  TaskManager.getInstance();
        int taskNum = tm.getTaskNumForFile(file);
        //LogicalPosition pos  =  selectedTextEditor.getCaretModel().getLogicalPosition();
        String docsfile = tm.getDocFileForTask(taskNum, pos, file);
        if (docsfile == null) {
            docsfile = "empty_study.docs";
        }
        InputStream ip = StudyDocumentationProvider.class.getResourceAsStream(docsfile);
        BufferedReader bf = new BufferedReader(new InputStreamReader(ip));
        StringBuilder text = new StringBuilder();
        try {
            while(bf.ready()) {
                String line = bf.readLine();
                text.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        return null;
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        return null;
    }

    @Override
    public PsiElement getCustomDocumentationElement(@NotNull final Editor editor, @NotNull final PsiFile file, @Nullable PsiElement contextElement) {
        return null;
    }
}
