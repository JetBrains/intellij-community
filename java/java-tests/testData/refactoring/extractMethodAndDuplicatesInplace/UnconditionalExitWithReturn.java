public class Test {
    public static void main(String[] args) {
        String xValue = null;
        for (String arg : args) {
            <selection>
            if (arg.endsWith("y")) {
                continue;
            }
            if (arg.startsWith("x")) {
                xValue = arg;
            }
            </selection>
        }
        System.out.println(xValue);
    }
}