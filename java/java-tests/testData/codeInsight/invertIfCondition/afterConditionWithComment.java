// "Invert 'if' condition" "true"
class A {
    // IDEA-153371
    public void foo() {
        String value ="not-null";

        // Another comment
        if (value == null) {
            return;
        }
        System.out.println(value);
        // Comment gets deleted.
    }
}