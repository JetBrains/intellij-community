interface Test {
    int implicitStatic = 42;

    default void test(){
        <selection>System.out.println(implicitStatic);</selection>
    }
}