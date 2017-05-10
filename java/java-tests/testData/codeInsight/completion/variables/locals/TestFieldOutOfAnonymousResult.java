package codeInsight.completion.variables.locals;

public class TestSource9 {
    private int aField;
    public void foo() {
        Runnable r = new Runnable() {
            public void run() {
               int x = aField<caret>;
            }
        };
    }
}
