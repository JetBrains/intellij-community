package com.jetbrains.jsonSchema.ide;


import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class JsonSchemaAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    JsonSchemaService service = getService(element);
    if (service != null) {
      Annotator delegate = service.getAnnotator(element.getContainingFile().getVirtualFile());
      if (delegate != null) {
        delegate.annotate(element, holder);
      }
    }
  }

  protected JsonSchemaService getService(@NotNull PsiElement element) {
    return JsonSchemaService.Impl.get(element.getProject());
  }
}
