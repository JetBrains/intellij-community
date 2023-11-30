public class Main {
    int i = 3;

    public void foo() {
        do System.out.println(<caret>i++);
        while (true);
    }
}