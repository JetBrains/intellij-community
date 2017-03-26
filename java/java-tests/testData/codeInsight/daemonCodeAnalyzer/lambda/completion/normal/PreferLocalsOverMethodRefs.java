import java.util.function.*;

class Foo {
  void foo(PsiElement psiElement) {
    Function<PsiElement, PsiElement> f = psi<caret>
  }
}

interface PsiElement {
  PsiElement getParent();
}