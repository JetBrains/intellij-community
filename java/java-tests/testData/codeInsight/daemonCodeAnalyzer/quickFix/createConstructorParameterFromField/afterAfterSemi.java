// "Add constructor parameter" "true"
public class A {
    private final A field;

    public A(A field) {
        this.field = field;<caret>
    }
}