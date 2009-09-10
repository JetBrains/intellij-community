class InStaticInitializer {
    public static final String x = "Hello World";

    static {
		System.out.println(x);
    }
    //Field must be placed before initializer or illegal forward reference will happen
}