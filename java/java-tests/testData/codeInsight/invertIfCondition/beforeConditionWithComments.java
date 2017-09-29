// "Invert 'if' condition" "true"
class A {
    public void foo() {
        String value ="not-null";

        <caret>if (value != null) {
            /* block*/
            // Before
            System.out.println(value);
            /* inside */
            System.out.println(value);
            // After
            /* end block*/
        }
    }
}