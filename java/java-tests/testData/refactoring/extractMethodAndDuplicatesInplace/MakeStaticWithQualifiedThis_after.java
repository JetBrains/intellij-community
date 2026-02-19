public class Test {

    int x;

    public void run() {
        extracted(this.x);
    }

    private static void extracted(int x) {
        System.out.println(x);
    }
}