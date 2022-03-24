// "Change type of 'a' to 'Integer' and remove cast" "true"
class WeakVariableQuickFix {
    void perform() {
        Object b;
        Integer a;

        a = get();
        b = new Object();

        System.out.println(a.byteValue());
    }

    native Integer get();
}