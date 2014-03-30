import java.io.Serializable;

abstract class Test {

    abstract <T> T test(Class<T> cls);

    abstract <T> T test(Serializable type);

    private void call(){
        <error descr="Incompatible types. Found: 'java.lang.String[]', required: 'java.lang.String'">String s = test(String[].class);</error>
    }
}