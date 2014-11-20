import java.util.Map;

public class SOE {

    public static <K extends M, M extends Map<K,M>> M foo() {return null;}
    public static <K1 extends M1, M1 extends Map<K1,M1>> Map<K1, M1> foo1() {<error descr="Incompatible types. Found: 'M', required: 'java.util.Map<K1,M1>'">return foo();</error>}
}
