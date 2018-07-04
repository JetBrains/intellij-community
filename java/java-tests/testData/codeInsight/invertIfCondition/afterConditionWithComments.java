// "Invert 'if' condition" "true"
class A {
    public void foo() {
        String value ="not-null";

        if (value == null) {
            return;
        }
        /* block*/
        // Before
        System.out.println(value);
        /* inside */
        System.out.println(value);
        // After
        /* end block*/
    }
}