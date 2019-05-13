// "Create read-only property 'field' in 'Test'" "true"
public class Test {
    Integer field;
    public foo() {
        get<caret>Field();
    }
}
