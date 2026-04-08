public class Test {
    public static void main(String[] args) {
        String xValue = null;
        for (String arg : args) {

            xValue = getXValue(arg, xValue);
            continue;

        }
        System.out.println(xValue);
    }

    private static String getXValue(String arg, String xValue) {
        if (arg.endsWith("y")) {
            return xValue;
        }
        if (arg.startsWith("x")) {
            xValue = arg;
        }
        return xValue;
    }
}