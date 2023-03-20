// "Create constant field 'FIELD'" "true-preview"
class C {
    public void foo() {
        int i = ITest.FIELD<caret>;
    }
    
    public static interface ITest {
    }
}