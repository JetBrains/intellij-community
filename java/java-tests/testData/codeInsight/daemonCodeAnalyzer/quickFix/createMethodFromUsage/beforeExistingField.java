// "Create read-only property 'field' in 'Test'" "true-preview"
public class Test {
    Integer field;
    public foo() {
        get<caret>Field();
    }
}
