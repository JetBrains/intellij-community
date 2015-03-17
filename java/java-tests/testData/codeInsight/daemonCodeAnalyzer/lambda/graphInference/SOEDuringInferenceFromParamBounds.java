import java.util.Map;

class SOE {

    public static <K extends M, M extends Map<K,M>> M foo() {return null;}
    public static <K1 extends M1, M1 extends Map<K1,M1>> Map<K1, M1> foo1() {return foo();}
}
