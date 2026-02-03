class Util {
    static void foo() { }

    public static void main(String[] args) throws Throwable {
        Util.class.getMethod("foo");
    }
}