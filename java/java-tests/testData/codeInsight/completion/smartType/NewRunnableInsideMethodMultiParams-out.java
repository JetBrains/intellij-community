public class Test {

    public static void run(int times, Runnable runnable) {
    }

    public static void main(String[] args) {
        run(123, new Runnable() {
            @Override
            public void run() {
                <caret>
            }
        });
    }
}
