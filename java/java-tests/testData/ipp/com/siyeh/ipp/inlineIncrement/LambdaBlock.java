public class Main {
    public void foo() {
        I i = n -> {n<caret>++; System.out.println(n);}
    }
    interface I {int get(int n);}
}