public class Test {
    public static void main(String[] args) {
        final String s = "dude";
        newMethod(s);
    }

    private static void newMethod(final String s) {
        Runnable runnable = new Runnable() {
            public void run() {
                System.out.println(s);
            }
        };
        new Thread(runnable).start();
    }
}