// "Change type of 'a' to 'Integer' and remove cast" "true"
class WeakVariableQuickFix {
    void perform() {
        Integer a;
        Object b;

        a = get();
        b = new Object();

        System.out.println(a.byteValue());
    }

    native Integer get();
}