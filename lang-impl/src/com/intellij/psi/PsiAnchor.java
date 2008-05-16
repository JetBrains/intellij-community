package com.intellij.psi;

import com.intellij.extapi.psi.StubPath;
import com.intellij.extapi.psi.StubPathBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 26, 2004
 * Time: 7:03:41 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class PsiAnchor {
  @Nullable
  public abstract PsiElement retrieve();
  public abstract PsiFile getFile();
  public abstract int getStartOffset();
  public abstract int getEndOffset();

  public static PsiAnchor create(PsiElement element) {
    if (element instanceof PsiCompiledElement) {
      return new HardReference(element);
    }

    PsiFile file = element.getContainingFile();
    if (file == null) {
      return new HardReference(element);
    }

    if (element instanceof StubBasedPsiElement && element.isPhysical() && ((PsiFileImpl)file).getContentElementType() instanceof IStubFileElementType) {
      final StubBasedPsiElement elt = (StubBasedPsiElement)element;
      if (elt.getStub() != null || elt.getElementType().shouldCreateStub(element.getNode())) {
        return new StubPathReference(file, StubPathBuilder.build((StubBasedPsiElement)element));
      }
    }

    TextRange textRange = element.getTextRange();
    if (textRange == null) {
      return new HardReference(element);
    }

    Language lang = null;
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<Language> languages = viewProvider.getLanguages();
    for (Language l : languages) {
      if (PsiTreeUtil.isAncestor(viewProvider.getPsi(l), element, false)) {
        lang = l;
        break;
      }
    }

    if (lang == null) lang = element.getLanguage();
    return new TreeRangeReference(file, textRange.getStartOffset(), textRange.getEndOffset(), element.getClass(), lang);
  }

  private static class TreeRangeReference extends PsiAnchor {
    private final PsiFile myFile;
    private final Language myLanguage;
    private final int myStartOffset;
    private final int myEndOffset;
    private final Class myClass;

    private TreeRangeReference(final PsiFile file, final int startOffset, final int endOffset, final Class aClass, final Language language) {
      myFile = file;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myClass = aClass;
      myLanguage = language;
    }

    @Nullable
    public PsiElement retrieve() {
      PsiElement element = myFile.getViewProvider().findElementAt(myStartOffset, myLanguage);
      if (element == null) return null;

      while  (!element.getClass().equals(myClass) ||
              element.getTextRange().getStartOffset() != myStartOffset ||
              element.getTextRange().getEndOffset() != myEndOffset) {
        element = element.getParent();
        if (element == null || element.getTextRange() == null) return null;
      }

      return element;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myEndOffset;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TreeRangeReference)) return false;

      final TreeRangeReference that = (TreeRangeReference)o;

      if (myEndOffset != that.myEndOffset) return false;
      if (myStartOffset != that.myStartOffset) return false;
      if (myClass != null ? !myClass.equals(that.myClass) : that.myClass != null) return false;
      if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      int result = myClass != null ? myClass.getName().hashCode() : 0;
      result = 31 * result + myStartOffset; //todo
      result = 31 * result + myEndOffset;
      if (myFile != null) {
        result = 31 * result + myFile.getName().hashCode();
      }

      return result;
    }
  }

  private static class HardReference extends PsiAnchor {
    private final PsiElement myElement;

    private HardReference(final PsiElement element) {
      myElement = element;
    }

    public PsiElement retrieve() {
      return myElement;
    }

    public PsiFile getFile() {
      return myElement.getContainingFile();
    }

    public int getStartOffset() {
      return myElement.getTextRange().getStartOffset();
    }

    public int getEndOffset() {
      return myElement.getTextRange().getEndOffset();
    }


    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof HardReference)) return false;

      final HardReference that = (HardReference)o;

      return myElement.equals(that.myElement);
    }

    public int hashCode() {
      return myElement.hashCode();
    }
  }

  private static class StubPathReference extends PsiAnchor {
    private final PsiFile myFile;
    private final StubPath myPath;

    public StubPathReference(final PsiFile file, final StubPath path) {
      myFile = file;
      myPath = path;
    }

    public PsiElement retrieve() {
      return StubPathBuilder.resolve(myFile, myPath);
    }

    public PsiFile getFile() {
      return myFile;
    }

    public int getStartOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null);
      return resolved.getTextRange().getStartOffset();
    }

    public int getEndOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null);
      return resolved.getTextRange().getEndOffset();
    }

    public boolean equals(final Object o) {
      if (this == o) return true;

      if (o instanceof StubPathReference) {
        final StubPathReference that = (StubPathReference)o;
        return myFile.equals(that.myFile) && myPath.equals(that.myPath);
      }

      return false;
    }

    public int hashCode() {
      return 31 * myFile.hashCode() + myPath.hashCode();
    }
  }

  
}

