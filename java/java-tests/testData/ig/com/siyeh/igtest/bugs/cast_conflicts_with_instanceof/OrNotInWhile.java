// IDEA-212568
class Test {
  void foo(Object containingClass) {
    while ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression)) {
      if (containingClass instanceof PsiLambdaExpression ||
          !check(((PsiAnonymousClass)containingClass).getArgumentList())) {
      }
      containingClass = next(containingClass);
    }
  }

  native boolean check(Object obj);
  native Object next(Object obj);
}
interface PsiAnonymousClass {
  Object getArgumentList();
}
interface PsiLambdaExpression {}
