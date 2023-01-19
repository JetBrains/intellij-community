// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(String o) {
        switch (o) {
            case "42", String s when<caret> s.isEmpty() -> {}
        }
    }
}
