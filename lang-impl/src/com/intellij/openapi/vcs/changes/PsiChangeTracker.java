package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
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

  public static <T extends PsiElement> Map<T, FileStatus> getElementsChanged(PsiFile file, final Class<T> filter) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    FileStatus status = FileStatusManager.getInstance(project).getStatus(file.getVirtualFile());
    final String oldText = getUnmodifiedDocument(file.getVirtualFile(), project);
    final DocumentImpl oldDocument = oldText == null ? null : new DocumentImpl(oldText);
    final LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    final List<T> elements = new ArrayList<T>();
    final List<T> oldElements = new ArrayList<T>();
    final PsiFile oldFile = oldText == null ? null : PsiFileFactory.getInstance(project)
      .createFileFromText(file.getName(), file.getLanguage(), oldText, false, true);

    file.accept(new MyVisitor<T>(filter, elements));
    if (oldFile != null) {
      oldFile.accept(new MyVisitor<T>(filter, oldElements));
    }
    
    final HashMap<T, FileStatus> result = new HashMap<T, FileStatus>();
    if (status == FileStatus.ADDED) {
      for (T element : elements) {
        result.put(element, status);
      }
      return result;
    }

    if (tracker == null) return result;
    
    for (Range range : tracker.getRanges()) {
        status = getRangeType(range);

          if (status == FileStatus.DELETED) {
            if (oldDocument == null) break;
            for (T element : oldElements) {
              if (getUpdatedTextRange(oldDocument, range).contains(element.getTextRange())) {
                result.put(element, status);
              }
            }
          } else if (status == FileStatus.ADDED || status == FileStatus.MODIFIED) {
            final TextRange old = getOldTextRange(document, range);
            final TextRange updated = getUpdatedTextRange(document, range);
            for (T element : elements) {
              if (isInserted(element, status, old) || isModified(element, status, old)) {
                result.put(element, status);
              }
            }

        }
    }

    return result;
  }

  private static <T extends PsiElement> boolean isInserted(T element, FileStatus status, TextRange range) {
    return status == FileStatus.ADDED && range.contains(element.getTextRange());
  }

  private static <T extends PsiElement> boolean isModified(T element, FileStatus status, TextRange range) {
    return status == FileStatus.MODIFIED && element.getTextRange().intersects(range);
  }

  public static TextRange getOldTextRange(final Document doc, final Range range) {
    return new TextRange(doc.getLineStartOffset(range.getOffset1()), doc.getLineEndOffset(range.getOffset2()));
  }

  public static TextRange getUpdatedTextRange(final Document doc, final Range range) {
    return new TextRange(doc.getLineStartOffset(range.getUOffset1()), doc.getLineEndOffset(range.getUOffset2()));
  }

  public static FileStatus getRangeType(@NotNull Range range) {
    switch (range.getType()) {
      case Range.MODIFIED: return FileStatus.MODIFIED;
      case Range.INSERTED: return FileStatus.ADDED;
      case Range.DELETED: return FileStatus.DELETED;
      default: throw new IllegalArgumentException("Unknown range type: " + range.getType());
    }
  }

  @Nullable
  public static String getUnmodifiedDocument(final VirtualFile file, Project project) {
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
        catch(VcsException ex) {
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
    private final Class<T> filter;
    private final List<T> elements;

    protected MyVisitor(final Class<T> filter, final List<T> elements) {
      this.filter = filter;
      this.elements = elements;
    }

    @Override
      public void visitElement(PsiElement element) {
        if (filter.isAssignableFrom(element.getClass())) {
          elements.add((T)element);
        }
        super.visitElement(element);
      }
    }
}
