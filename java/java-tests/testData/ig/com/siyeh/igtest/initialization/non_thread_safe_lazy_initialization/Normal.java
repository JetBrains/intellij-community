public class Normal {
    private /*1*/ static /*2*/ Object /*3*/ example/*4*/;

    public static Object getInstance() {
        // 5
        if (example == null) { //6
            <warning descr="Lazy initialization of 'static' field 'example' is not thread-safe">example<caret></warning> = new Object(); //7
        } //8
        return example;
    }
}