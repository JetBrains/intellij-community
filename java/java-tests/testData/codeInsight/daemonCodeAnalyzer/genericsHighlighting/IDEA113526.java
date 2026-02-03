import java.util.List;

class MyTest {

    static void m(List<? extends List> c) {
        List<? extends List<?>> d = <warning descr="Unchecked cast: 'java.util.List<capture<? extends java.util.List>>' to 'java.util.List<? extends java.util.List<?>>'">(List<? extends List<?>>)c</warning>;
        System.out.println(d);
    }

    public static void main(String[] args) {
        m(null);
    }
}