class InStaticInitializer {
    public static final String s = "Hello World";

    static {
		System.out.println(s);
    }
    //Field must be placed before initializer or illegal forward reference will happen
}