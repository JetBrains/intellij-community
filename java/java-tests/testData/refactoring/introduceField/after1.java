class InStaticInitializer {
    public static final String helloWorld = "Hello World";

    static {
		System.out.println(helloWorld);
    }
    //Field must be placed before initializer or illegal forward reference will happen
}