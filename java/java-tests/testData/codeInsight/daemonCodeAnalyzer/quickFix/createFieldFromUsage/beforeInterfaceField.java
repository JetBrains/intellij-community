// "Create field 'FIELD'" "false"
class C {
    public void foo() {
        int i = ITest.FIELD<caret>;
    }

    public static interface ITest {
    }
}