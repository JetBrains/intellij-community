class A {
 void assignment(double v, int x, int y) {
        String result;
<selection>
        result = format("digit %f", v, x, y );
</selection>
        System.out.println(result);
    }

}

class B {
    @FunctionalInterface
    private interface DII {
        String doIt(double d, int x, int y);
    }
}