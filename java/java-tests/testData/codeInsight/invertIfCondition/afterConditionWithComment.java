// "Invert 'if' condition" "true"
class A {
    // IDEA-153371
    public void foo() {
        String value ="not-null";

        if (value == null) {
            return; // Another comment
        }
        System.out.println(value);
        // Comment gets deleted.
    }
}