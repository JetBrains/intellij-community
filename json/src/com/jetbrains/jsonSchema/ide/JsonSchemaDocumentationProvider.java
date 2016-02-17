package com.jetbrains.jsonSchema.ide;


import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JsonSchemaDocumentationProvider implements DocumentationProvider {
  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    DocumentationProvider provider = getDelegateProvider(element);
    return provider != null ? provider.generateDoc(element, originalElement) : null;
  }

  @Nullable
  private static DocumentationProvider getDelegateProvider(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();

    if (containingFile != null) {
      VirtualFile file = containingFile.getVirtualFile();
      JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
      if (service != null) {
        return service.getDocumentationProvider(file);
      }
    }

    return null;
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
}
