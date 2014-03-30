import java.util.Map;

class Test {
    void bar(Prop p) {
         Map<? extends String, ? extends String> map = <error descr="Inconvertible types; cannot cast 'Prop' to 'java.util.Map<? extends java.lang.String,? extends java.lang.String>'">(Map<? extends String, ?  extends String>)p</error>;
    }
}

abstract class Hashtble<K,V> implements Map<K,V> {}
abstract class Prop extends Hashtble<Object, Object>{}
