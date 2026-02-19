// IDEA-217602
interface JSReferenceExpression {
  ResolveResult[] multiResolve(boolean incomplete);
}

interface ResolveResult {
  PsiElement getElement();
}

interface PsiElement {}

interface JSFunction extends PsiElement {
  boolean isConstructor();
}
interface JSClass extends PsiElement {

}

class Test1 {
  private void foo() {}
  private void checkRef(JSReferenceExpression methodExpr) {
    ResolveResult[] results = methodExpr.multiResolve(false);
    PsiElement elt;

    if (results.length > 0 &&
        ((elt = results[0].getElement()) instanceof JSFunction && ((JSFunction)elt).isConstructor() ||
         elt instanceof JSClass
        )) {
      foo();
    }
  }
}