public class JavaClass<T> {



    <V extends T> V foo(Class<V> s) {}
}

class Zoo {
    {
        Class<? extends String> xcccccccc = null;
        JavaClass<String> cccc = new JavaClass<String>();
        bar(cccc.foo(xcccccccc<caret>));
    }

    void bar(String... s) {

    }
}
