class Test1 {
    public static void method(Object o, boolean b) {
        System.out.println("Object");
    }

    public static void method(String s, Object o) {
        System.out.println("String");
    }

    public static void main(String[] args) {
        <ref>method("Hello, World", false);
    }

}