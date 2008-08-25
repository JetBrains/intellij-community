package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 *
 */
public class GotoTypeDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler{

  protected CodeInsightActionHandler getHandler(){
    return this;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public void update(final AnActionEvent event) {
    if (Extensions.getExtensions(TypeDeclarationProvider.EP_NAME).length == 0) {
      event.getPresentation().setVisible(false);
    }
    else {
      super.update(event);
    }
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement[] symbolTypes = findSymbolTypes(editor, offset);
    if (symbolTypes == null || symbolTypes.length == 0) return;
    if (symbolTypes.length == 1) {
      navigate(project, symbolTypes[0]);
    }
    else {
      NavigationUtil.getPsiElementPopup(symbolTypes, CodeInsightBundle.message("choose.type.popup.title")).showInBestPositionFor(editor);
    }
  }

  private static void navigate(@NotNull Project project, @NotNull PsiElement symbolType) {
    PsiElement element = symbolType.getNavigationElement();
    assert element != null;
    VirtualFile file = element.getContainingFile().getVirtualFile();
    OpenFileDescriptor descriptor=new OpenFileDescriptor(project, file, element.getTextOffset());
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public static PsiElement findSymbolType(Editor editor, int offset) {
    final PsiElement[] psiElements = findSymbolTypes(editor, offset);
    if (psiElements != null && psiElements.length > 0) return psiElements[0];
    return null;
  }

  @Nullable
  public static PsiElement[] findSymbolTypes(Editor editor, int offset) {
    PsiElement targetElement = TargetElementUtilBase.getInstance().findTargetElement(editor,
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
      TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
      TargetElementUtilBase.LOOKUP_ITEM_ACCEPTED,
      offset);

    if (targetElement != null) {
      final PsiElement[] symbolType = getSymbolTypeDeclarations(targetElement);
      return symbolType == null ? PsiElement.EMPTY_ARRAY : symbolType;
    }
    final PsiReference psiReference = TargetElementUtilBase.findReference(editor, offset);
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(false);
      Set<PsiElement> types = new THashSet<PsiElement>();

      for(ResolveResult r: results) {
        final PsiElement[] declarations = getSymbolTypeDeclarations(r.getElement());
        if (declarations != null) {
          for (PsiElement declaration : declarations) {
            types.add(declaration);
          }
        }
      }

      if (!types.isEmpty()) return types.toArray(new PsiElement[types.size()]);
    }

    return null;
  }

  @Nullable
  private static PsiElement[] getSymbolTypeDeclarations(final PsiElement targetElement) {
    for(TypeDeclarationProvider provider: Extensions.getExtensions(TypeDeclarationProvider.EP_NAME)) {
      PsiElement[] result = provider.getSymbolTypeDeclarations(targetElement);
      if (result != null) return result;
    }

    return null;
  }

}