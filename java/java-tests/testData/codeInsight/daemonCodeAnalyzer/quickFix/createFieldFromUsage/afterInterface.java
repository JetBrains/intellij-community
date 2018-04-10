// "Create constant field 'FIELD'" "true"
class C {
    public void foo() {
        int i = ITest.FIELD;
    }
    
    public static interface ITest {
        int FIELD = <caret>;
    }
}