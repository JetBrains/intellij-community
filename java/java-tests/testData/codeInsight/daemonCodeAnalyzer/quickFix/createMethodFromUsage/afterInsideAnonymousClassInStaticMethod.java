// "Create method 'doSomething' in 'Test'" "true"
public class Test {
    public static void main(String[] args) {
        blaat(new Runnable() {
            public void run() {
                doSomething();
            }
        });
    }

    private static void doSomething() {
        <caret>
    }

    public static void blaat(Runnable o) {}
}
