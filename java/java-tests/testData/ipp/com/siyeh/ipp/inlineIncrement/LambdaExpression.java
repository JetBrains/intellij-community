public class Main {
    public void foo() {
        I i = n -> n<caret>++;
    }
    interface I {int get(int n);}
}