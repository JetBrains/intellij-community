import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        x.add(str());
        baz();
    }//ins and outs
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod() {
        x.add(str());
        baz();
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar() {
        y.add(str());
        baz();
    }

    private String str() { return null; }
    private void baz() { }
}
