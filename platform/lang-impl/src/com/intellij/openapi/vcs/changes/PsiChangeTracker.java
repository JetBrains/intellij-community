package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class PsiChangeTracker {
  private PsiChangeTracker() {
  }

  public static <T extends PsiElement> Map<T, FileStatus> getElementsChanged(PsiFile file, final PsiElementFilter<T> filter) {
    final Project project = file.getProject();
    final String oldText = getUnmodifiedDocument(file.getVirtualFile(), project);
    //TODO: make loop for different languages
    //TODO: for ( PsiFile f : file.getViewProvider().getAllFiles() )
    //TODO: for some languages (eg XML) isEquivalentTo works ugly. Think about pluggable matchers for different languages/elements
    final PsiFile oldFile = oldText == null
                            ? null : PsiFileFactory.getInstance(project).createFileFromText(oldText, file);
    return getElementsChanged(file, oldFile, filter);
  }

  public static <T extends PsiElement> Map<T, FileStatus> getElementsChanged(PsiFile file, PsiFile oldFile, final PsiElementFilter<T> filter) {
    final Project project = file.getProject();
    final List<T> elements = new ArrayList<T>();
    final List<T> oldElements = new ArrayList<T>();

    file.accept(new MyVisitor<T>(filter, elements));
    final VirtualFile vf = file.getVirtualFile();
    final FileStatus status = vf == null ? null : FileStatusManager.getInstance(project).getStatus(vf);
    final HashMap<T, FileStatus> result = new HashMap<T, FileStatus>();
    if (status == FileStatus.ADDED ||
        status == FileStatus.DELETED ||
        status == FileStatus.DELETED_FROM_FS ||
        status == FileStatus.UNKNOWN) {
      for (T element : elements) {
        result.put(element, status);
      }
      return result;
    }

    if (oldFile == null) return result;
    oldFile.accept(new MyVisitor<T>(filter, oldElements));
    calculateStatuses(elements, oldElements, result);

    return result;
  }

  private static <T extends PsiElement> Map<T, FileStatus> calculateStatuses(List<T> elements,
                                                                             List<T> oldElements,
                                                                             Map<T, FileStatus> result) {
    for (T element : elements) {
      T e = null;
      for (T oldElement : oldElements) {
        if (element.isEquivalentTo(oldElement)) {
          e = oldElement;
          break;
        }
      }
      if (e != null) {
        oldElements.remove(e);
        if (!element.getText().equals(e.getText())) {
          result.put(element, FileStatus.MODIFIED);
        }
      }
      else {
        result.put(element, FileStatus.ADDED);
      }
    }

    for (T oldElement : oldElements) {
      result.put(oldElement, FileStatus.DELETED);
    }

    return result;
  }

  @Nullable
  private static String getUnmodifiedDocument(final VirtualFile file, Project project) {
    final Change change = ChangeListManager.getInstance(project).getChange(file);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision instanceof BinaryContentRevision) {
        return null;
      }
      if (beforeRevision != null) {
        String content;
        try {
          content = beforeRevision.getContent();
        }
        catch (VcsException ex) {
          content = null;
        }
        return content == null ? null : StringUtil.convertLineSeparators(content);
      }
      return null;
    }

    final Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null && document.getModificationStamp() != file.getModificationStamp()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return LoadTextUtil.loadText(file).toString();
        }
      });
    }

    return null;
  }

  static class MyVisitor<T extends PsiElement> extends PsiRecursiveElementVisitor {
    private final PsiElementFilter<T> filter;
    private final List<T> elements;

    protected MyVisitor(final PsiElementFilter<T> filter, final List<T> elements) {
      this.filter = filter;
      this.elements = elements;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (filter.getClassFilter().isAssignableFrom(element.getClass())) {
        final T e = (T)element;
        if (filter.accept(e)) {
          elements.add(e);
        }
      }
      super.visitElement(element);
    }
  }
}
