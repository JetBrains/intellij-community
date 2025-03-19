public class Normal {

    private static final class ExampleHolder {
        /*1*/ static final /*2*/ Object /*3*/ example/*4*/ = new Object();
    }

    public static Object getInstance() {
        // 5
        //6
        //7
        //8
        return ExampleHolder.example;
    }
}