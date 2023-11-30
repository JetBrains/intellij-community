public class Main {
    int i = 3;

    public void foo() {
        ++<caret>i; // comment
        System.out.println(i);
    }
}