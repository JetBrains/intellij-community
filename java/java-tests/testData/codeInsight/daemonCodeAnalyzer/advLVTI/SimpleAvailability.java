import java.util.function.Function;

class Main {
    private static void localVariableDeclaration() {
        var a = 1;
        <error descr="'var' is not allowed in a compound declaration">var b = 2</error>, <error descr="'var' is not allowed in a compound declaration">c;</error>
        <error descr="'var' is not allowed as an element type of an array">var</error> d[] = new int[4];
        var d1 = new int[] {4};
        var d2 = new int[4];
        <error descr="Cannot infer type: 'var' on variable without initializer">var</error> e;
        var f = <error descr="Array initializer is not allowed here">{ 6 }</error>;
        var g = (<error descr="Cannot infer type for 'g', it is used in its own variable initializer">g</error> = 7);
        var x = (<error descr="Cannot infer type for 'x', it is used in its own variable initializer">x</error> = 1) + 1;
        var y = new Object[] {<error descr="Cannot infer type for 'y', it is used in its own variable initializer">y</error> = null};
        var z = baz(<error descr="Cannot infer type for 'z', it is used in its own variable initializer">z</error> = 1);
    }
    
    static int baz(Object o) {return 42;}

    private static  void localVariableType() {
        var a =  1;
        int al = a;

        var b = java.util.Arrays.asList(1, 2);
        Integer bl = b.get(0);

        var c = "x".getClass();
        Class<? extends String> cl = c;

        var d = new Object() {};

        var e = (CharSequence & Comparable<String>) "x";
        int el = e.compareTo("");

        <error descr="Cannot infer type: lambda expression requires an explicit target type">var</error> f = () -> "hello";
        <error descr="Cannot infer type: method reference requires an explicit target type">var</error> m = Main::localVariableDeclaration;
        <error descr="Cannot infer type: variable initializer is 'null'">var</error> g = null;
        var runnable = true ? <error descr="Lambda expression not expected here">() -> {}</error> : <error descr="Lambda expression not expected here">() -> {}</error>;

        Function<String, String> f1 = (<error descr="Cannot resolve symbol 'var'">var</error> var) -> var;

        <error descr="Cannot infer type: variable initializer is 'void'">var</error> h = localVariableDeclaration();
        <error descr="Cannot infer type: variable initializer is 'null'">var</error> cond = true ? null : null;
    }

    private void forEachType(String[] strs, Iterable<String> it, Iterable raw) {
        for (var str: strs) {
            String s = str;
        }

        for (var str: it) {
            String s = str;
            str = s;
        }

        for (var o: raw) {
            Object obj = o;
        }

        for (var v:<error descr="Expression expected"> </error>) {}

        for (var v: <error descr="foreach not applicable to type 'null'">null</error>) {}

        for (var v: (<error descr="Cannot resolve symbol 'v'">v</error>)) {}
    }

    private void tryWithResources(AutoCloseable c) throws Exception {
        try (<error descr="Cannot infer type: variable initializer is 'null'">var</error> v = null) { }
        try (var v = c; var v1 = c) { }
    }

    class Hello {}
    private void checkUnresolvedTypeInForEach(String[] iterableStrings,
                                              Hello iterableExistingHello,
                                              <error descr="Cannot resolve symbol 'World'">World</error> iterableUnresolvedWorld) {
        for (String arg: iterableStrings) { }
        for (String arg: <error descr="foreach not applicable to type 'Main.Hello'">iterableExistingHello</error>) { }
        for (String arg: iterableUnresolvedWorld) {}
    }

    interface A {}
    interface B {}
    void test(Object x) {
        java.util.Optional.of((A & B)x).ifPresent(valueOfAandB -> {
            for(Object foo : <error descr="foreach not applicable to type 'Main.A & Main.B'">valueOfAandB</error>) {
                System.out.println(foo);
            }
        });
        java.util.Optional.of((A & <error descr="Cannot resolve symbol 'World'">World</error>)x).ifPresent(valueOfAandUnresolvedWorld -> {
            for(Object foo : <error descr="foreach not applicable to type 'Main.A & World'">valueOfAandUnresolvedWorld</error>) {
                System.out.println(foo);
            }
        });
    }
}