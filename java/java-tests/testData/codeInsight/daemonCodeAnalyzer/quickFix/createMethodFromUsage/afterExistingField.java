// "Create read-only property 'field' in 'Test'" "true"
public class Test {
    Integer field;
    public foo() {
        getField();
    }

    public Object getField() {
        return field;
    }
}
