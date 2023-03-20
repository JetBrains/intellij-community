// "Create inner record 'EmptyBox'" "true-preview"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case Empty<caret>Box() -> System.out.println( "Fill it up and send it back");
            default -> {}
        }
    }
}