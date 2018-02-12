import java.io.Serializable;

abstract class Test {

    abstract <T> T test(Class<T> cls);

    abstract <T> T test(Serializable type);

    private void call(){
        String s = <error descr="Incompatible types. Required String but 'test' was inferred to T:
no instance(s) of type variable(s)  exist so that String[] conforms to String">test(String[].class);</error>
    }
}
