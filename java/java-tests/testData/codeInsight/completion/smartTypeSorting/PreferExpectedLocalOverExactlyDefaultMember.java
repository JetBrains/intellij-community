class Usage {
  void foo(PsiMethod method) {
    PsiUtil.ensureValid(<caret>);
  }
}

class PsiUtil {
  static final PsiElement NULL_PSI_ELEMENT;

  static void ensureValid(PsiElement e) {}
}

interface PsiElement {}
interface PsiMethod extends PsiElement {}