public class Test {
    public static void main(String[] args) {
        String xValue = null;
        for (String arg : args) {

            xValue = getString(arg, xValue);
            continue;

        }
        System.out.println(xValue);
    }

    private static String getString(String arg, String xValue) {
        if (arg.endsWith("y")) {
            return xValue;
        }
        if (arg.startsWith("x")) {
            xValue = arg;
        }
        return xValue;
    }
}