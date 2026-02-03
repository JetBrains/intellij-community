// "Copy 'switch' branch" "true-preview"
class C {
    void foo(String s) {
        String result = "";
        switch (s) {
            case "foo":
            <caret>case null:
            case "bar":
                result = "x";
                break;
        }
    }
}