// "Create constant field 'FIELD'" "true-preview"
class C {
    public void foo() {
        int i = ITest.FIELD;
    }
    
    public static interface ITest {
        int FIELD = <caret>;
    }
}