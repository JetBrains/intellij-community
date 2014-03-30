interface PsiElement {
  PsiElement findElementAt(int offset);
}

interface PsiFile extends PsiElement {
}

public class TestCompletion(){
  void m(){
    PsiElement e = <caret>
  }
}


