import java.util.Map;

public class SOE {

    public static <K extends M, M extends Map<K,M>> M foo() {return null;}
    public static <K1 extends M1, M1 extends Map<K1,M1>> Map<K1, M1> foo1() {return <error descr="Inferred type 'java.util.Map<K1,M1>' for type parameter 'M' is not within its bound; should implement 'java.util.Map<java.util.Map<K1,M1>,java.util.Map<K1,M1>>'">foo()</error>;}
}
