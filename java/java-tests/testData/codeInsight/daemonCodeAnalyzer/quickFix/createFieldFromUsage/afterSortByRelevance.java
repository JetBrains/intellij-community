import foo.Foo;

// "Create field 'field'" "true-preview"
class A {

    private Foo field;

    void bar() {
        field.put("a", "b");
    }

}