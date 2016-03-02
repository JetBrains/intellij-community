package com.jetbrains.jsonSchema.ide;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class JsonSchemaCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    Project project = parameters.getEditor().getProject();
    assert project != null;

    JsonSchemaService service = JsonSchemaService.Impl.get(project);
    assert service != null;

    CompletionContributor delegate = service.getCompletionContributor(parameters.getOriginalFile().getVirtualFile());
    if (delegate != null) {
      delegate.fillCompletionVariants(parameters, result);
    }
  }
}
