import java.io.Serializable;

abstract class Test {

    abstract <T> T test(Class<T> cls);

    abstract <T> T test(Serializable type);

    private void call(){
        String s = test<error descr="'test(java.lang.Class<T>)' in 'Test' cannot be applied to '(java.lang.Class<java.lang.String[]>)'">(String[].class)</error>;
    }
}
