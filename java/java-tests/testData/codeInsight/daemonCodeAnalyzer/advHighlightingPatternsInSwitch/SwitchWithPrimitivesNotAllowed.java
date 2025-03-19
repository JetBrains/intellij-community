package dfa;

public class SwitchWithPrimitivesNotAllowed {
    public static void main(String[] args) {
        testPrimitives();
        testWrappers();
    }

    private static void testWrappers() {
        Boolean b = true;
        switch (b) {
            default -> System.out.println("1");
        }
        Integer i = 1;
        switch (i) {
            default -> System.out.println("1");
        }
        Short s = 1;
        switch (s) {
            default -> System.out.println("1");
        }
        Byte by = 1;
        switch (by) {
            default -> System.out.println("1");
        }
        Character ch = '1';
        switch (ch) {
            default -> System.out.println("1");
        }
        Long l = 1L;
        switch (l) {
            default -> System.out.println("1");
        }
        Double d = 1.0;
        switch (d) {
            default -> System.out.println("1");
        }
        Float f = 1.0F;
        switch (f) {
            default -> System.out.println("1");
        }
    }

    private static void testPrimitives() {
        boolean b = true;
        switch (<error descr="Selector type of 'boolean' is not supported at language level '22'">b</error>) {//error
            default -> System.out.println("1");
        }
        int i = 1;
        switch (i) {
            default -> System.out.println("1");
        }
        short s = 1;
        switch (s) {
            default -> System.out.println("1");
        }
        byte by = 1;
        switch (by) {
            default -> System.out.println("1");
        }
        char ch = '1';
        switch (ch) {
            default -> System.out.println("1");
        }
        long l = 1L;
        switch (<error descr="Selector type of 'long' is not supported at language level '22'">l</error>) { //error
            default -> System.out.println("1");
        }
        double d = 1.0;
        switch (<error descr="Selector type of 'double' is not supported at language level '22'">d</error>) {//error
            default -> System.out.println("1");
        }
        float f = 1.0F;
        switch (<error descr="Selector type of 'float' is not supported at language level '22'">f</error>) {//error
            default -> System.out.println("1");
        }
    }
}
