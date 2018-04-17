// "Invert 'if' condition" "true"
class A {
    // IDEA-153371
    public void foo() {
        String value ="not-null";

        <caret>if (value != null) {
            System.out.println(value);
            // Comment gets deleted.
        } // Another comment
    }
}