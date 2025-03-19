import java.io.Serializable;

abstract class Test {

    abstract <T> T test(Class<T> cls);

    abstract <T> T test(Serializable type);

    private void call(){
        String s = <error descr="Incompatible types. Found: 'java.lang.String[]', required: 'java.lang.String'">test</error>(String[].class);
    }
}
