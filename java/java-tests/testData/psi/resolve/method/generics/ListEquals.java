import java.util.List;

class SomeClass {
    private void method(List<String> var, List<String> bar) {
        var.eq<caret>uals(bar);
    }
}