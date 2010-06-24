public class Autoboxing {
    void method(int i) {
        System.out.println("i = " + i);
    }

    void method(Integer integer) {
        System.out.println("integer = " + integer);
    }

    void m1(Integer integer) { }
    void m2(int i) { }

    {
        method(10);
        method(new Integer(10));
        m1(10);
        m1(new Integer(10));
        m2(10);
        m2(new Integer(10));
    }
}
public class Autoboxing1 {
    void method(String s, int i) {
        System.out.println("i = " + i);
    }

    void method(String s, Object o) {
        System.out.println("integer = " + o);
    }

    {
        method("abc", new Integer(10));
        method("abc", 10);
    }
}

class BoxingConflict {
    public static void main(String[] args) {
        add<error descr="Ambiguous method call: both 'BoxingConflict.add(long, Long)' and 'BoxingConflict.add(Long, Long)' match">(0L, 0L)</error>;
    }

    public static void add(long k, Long v) { }
    public static void add(Long k, Long v) { }
}
