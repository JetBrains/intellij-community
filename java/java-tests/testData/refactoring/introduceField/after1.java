class InStaticInitializer {
    public static final String hello_world = "Hello World";

    static {
		System.out.println(hello_world);
    }
    //Field must be placed before initializer or illegal forward reference will happen
}