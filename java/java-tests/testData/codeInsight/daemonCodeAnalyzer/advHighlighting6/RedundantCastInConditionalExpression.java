class Foo {
    public static void main(String[] args) {
        final boolean b = false;
        final Integer i = (<warning descr="Casting '3' to 'Integer' is redundant">Integer</warning>)3;
        final short s = (short)(b ? i : (int)Integer.valueOf(3));
        System.out.println(s);
    }
}
