// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(String o) {
        switch (o) {
            case "42" when s.isEmpty() -> {}
            case String s when s.isEmpty() -> {}
        }
    }
}
