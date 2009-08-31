/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.ContentBasedClassFileProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClsStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull final FileType fileType, @NotNull final VirtualFile file, @NotNull final Project project) {

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    final ContentBasedClassFileProcessor[] processors = Extensions.getExtensions(ContentBasedClassFileProcessor.EP_NAME);
    for (ContentBasedClassFileProcessor processor : processors) {
      if (processor.isApplicable(project, file)) {
        final Language language = processor.obtainLanguageForFile(file);
        if (language != null) {
          final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(language);
          if (factory != null) return factory.getStructureViewBuilder(psiFile);
        }
      }
    }

    final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
    if (javaFile == null) return null;
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new JavaFileTreeModel(javaFile);
      }
    };
  }
}