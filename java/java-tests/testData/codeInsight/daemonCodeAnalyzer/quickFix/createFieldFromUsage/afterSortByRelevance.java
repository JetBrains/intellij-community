import foo.Foo;

// "Create Field 'field'" "true"
class A {

    private Foo field;

    void bar() {
        field.put("a", "b");
    }

}