// "Change type of 'a' to 'Integer' and remove cast" "true"
class WeakVariableQuickFix {
    void perform() {
        Object a, b;

        a = get();
        b = new Object();

        System.out.println(((<caret>Integer)a).byteValue());
    }

    native Integer get();
}