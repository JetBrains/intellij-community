import java.io.Serializable;

abstract class Test {

    abstract <T> T test(Class<T> cls);

    abstract <T> T test(Serializable type);

    private void call(){
        String s = <error descr="no instance(s) of type variable(s)  exist so that String[] conforms to String
inference variable T has incompatible bounds:
 equality constraints: String[]
upper bounds: Object, String">test(String[].class);</error>
    }
}
