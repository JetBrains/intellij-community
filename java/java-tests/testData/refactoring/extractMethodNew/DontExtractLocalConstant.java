class Test {
    final String f1 = "field";
    String test(boolean condition){
        <selection>int x = 42;
        final String f2 = "variable";
        if (condition) return f1 + f2 + "literal";
        if (!condition) return f1+f2+"literal";</selection>
        System.out.println(x);
        return "default";
    }
}