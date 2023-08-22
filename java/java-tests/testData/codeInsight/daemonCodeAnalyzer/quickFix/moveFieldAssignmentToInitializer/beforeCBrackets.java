// "Move assignment to field declaration" "true-preview"
public class Foo {

    protected boolean isString[]    ;

    public Foo() {
        isSt<caret>ring = new boolean[123];
    }
}