
class Main {
    private static void localVariableDeclaration() {
        var a = 1;
        <error descr="'var' is not allowed in a compound declaration">var b = 2</error>, <error descr="'var' is not allowed in a compound declaration">c = 3.0;</error>
        <error descr="'var' is not allowed as an element type of an array">var</error> d[] = new int[4];
        var d1 = new int[] {4};                                                                                          
        var d2 = new int[4];                                                                                          
        <error descr="Cannot infer type: 'var' on variable without initializer">var</error> e;
        var f = <error descr="Array initializer is not allowed here">{ 6 }</error>;
        var g = (<error descr="Incompatible types. Found: 'int', required: 'null'">g = 7</error>);
    }

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
        var runnable = true ? <error descr="<lambda expression> is not a functional interface">() -> {}</error> : <error descr="<lambda expression> is not a functional interface">() -> {}</error>;
    }

   private void forEachType(String[] strs, Iterable<String> it, Iterable raw) {
        for (var str : strs) {
            String s = str;
        }

        for (var str : it) {
            String s = str;
            str = s;
        }

        for (var  o : raw) {
            Object obj = o;
        }

        for (var v:<error descr="Expression expected"> </error>) {}

        for (var v:  <error descr="foreach not applicable to type 'null'">null</error>) {}

        for (var v : (<error descr="Cannot resolve symbol 'v'">v</error>)) {}
    }

    private void tryWithResources(AutoCloseable c) throws Exception {
        try (<error descr="Cannot infer type: variable initializer is 'null'">var</error> v = null) { }
        try (var v = c; var v1 = c) { }

    }
}
