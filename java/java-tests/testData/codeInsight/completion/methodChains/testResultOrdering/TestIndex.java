import java.lang.String;

public class TestIndex {

  void m() {
    PsiManager.getInstance(null).findFile(null);
    PsiManager.getInstance(null).findFile(null);
    PsiManager.getInstance(null).findFile(null);
    PsiManager.getInstance(null).findFile(null);
  }

  void m2() {
    PsiDocumentManager.getInstance(null).getPsiFile(null);
    PsiDocumentManager.getInstance(null).getPsiFile(null);
    PsiDocumentManager.getInstance(null).getPsiFile(null);
    PsiDocumentManager.getInstance(null).getPsiFile(null);
  }

  void m3() {
    PsiFileFactory f = null;
    f.createFileFromText("");
    f.createFileFromText("");
    f.createFileFromText("");
    f.createFileFromText("");
  }

  void m4() {
    PsiClass c = null;
    c.getContainingClass();
    c.getContainingClass();
    c.getContainingClass();
    c.getContainingClass();
  }

}


interface Project {
}

interface PsiFile {
}

interface VirtualFile {
}

interface Document {
}

class PsiManager {
  static PsiManager getInstance(Project p) {
    return null;
  }

  PsiFile findFile(VirtualFile f) {
    return null;
  }
}

class PsiDocumentManager {
  static PsiDocumentManager getInstance(Project p) {
    return null;
  }

  PsiFile getPsiFile(Document d) {
    return null;
  }
}

interface PsiFileFactory {
  PsiFile createFileFromText(String s);
}

interface PsiClass {
  PsiFile getContainingClass();
}