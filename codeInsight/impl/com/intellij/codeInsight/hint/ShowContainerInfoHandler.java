package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;

import java.awt.*;
import java.lang.ref.WeakReference;

public class ShowContainerInfoHandler implements CodeInsightActionHandler {
  private static final Key<WeakReference<LightweightHint>> MY_LAST_HINT_KEY = Key.create("MY_LAST_HINT_KEY");
  private static final Key<PsiElement> CONTAINER_KEY = Key.create("CONTAINER_KEY");

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement container = null;
    WeakReference<LightweightHint> ref = editor.getUserData(MY_LAST_HINT_KEY);
    if (ref != null){
      LightweightHint hint = ref.get();
      if (hint != null && hint.isVisible()){
        hint.hide();
        container = hint.getUserData(CONTAINER_KEY);
        if (!container.isValid()){
          container = null;
        }
      }
    }

    if (container == null){
      int offset = editor.getCaretModel().getOffset();
      container = file.findElementAt(offset);
      if (container == null) return;
    }

    if (file instanceof PsiJavaFile || file instanceof XmlFile) {
      while(true){
        container = findContainer(container);
        if (container == null) return;
        if (!isDeclarationVisible(container, editor)) break;
      }
    }
    else {
      container = null;
      StructureViewBuilder builder = file.getLanguage().getStructureViewBuilder(file);
      if (builder instanceof TreeBasedStructureViewBuilder) {
        StructureViewModel model = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel();
        Object element = model.getCurrentEditorElement();
        if (element instanceof PsiElement) {
          container = (PsiElement) element;
          while(true) {
            if (container == null || container instanceof PsiFile) {
              return;
            }
            if (!isDeclarationVisible(container, editor)) {
              break;
            }

            container = container.getParent();
            while(container != null && DeclarationRangeUtil.getPossibleDeclarationAtRange(container) == null) {
              container = container.getParent();
              if (container instanceof PsiFile) return;
            }
          }
        }
      }
      if (container == null) {
        return;
      }
    }

    final TextRange range = DeclarationRangeUtil.getDeclarationRange(container);
    final PsiElement _container = container;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          LightweightHint hint = EditorFragmentComponent.showEditorFragmentHint(editor, range, true);
          hint.putUserData(CONTAINER_KEY, _container);
          editor.putUserData(MY_LAST_HINT_KEY, new WeakReference<LightweightHint>(hint));
        }
      });
  }

  public boolean startInWriteAction() {
    return false;
  }

  private PsiElement findContainer(PsiElement element) {
    PsiElement container = element.getParent();
    while(true){
      if (container instanceof PsiFile) return null;
      if (container instanceof PsiMethod || container instanceof PsiClass || container instanceof PsiClassInitializer) break;
      if (container instanceof XmlTag) break;
      container = container.getParent();
    }
    return container;
  }

  private boolean isDeclarationVisible(PsiElement container, Editor editor) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    TextRange range = DeclarationRangeUtil.getDeclarationRange(container);
    LogicalPosition pos = editor.offsetToLogicalPosition(range.getStartOffset());
    Point loc = editor.logicalPositionToXY(pos);
    return loc.y >= viewRect.y;
  }
}