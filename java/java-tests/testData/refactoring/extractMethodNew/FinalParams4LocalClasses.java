class Test {

    public static void main(String[] args) {
        final String s = "text";
        <selection>class A {
            {
                System.out.println(s);
            }
        }</selection>
    }
}