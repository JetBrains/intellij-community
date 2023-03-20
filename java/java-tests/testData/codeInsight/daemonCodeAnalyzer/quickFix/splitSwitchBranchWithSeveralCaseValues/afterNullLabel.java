// "Copy 'switch' branch" "true-preview"
class C {
    void foo(String s) {
        String result = "";
        switch (s) {
            case "foo":
            case "bar":
                result = "x";
                break;
            case null:
                result = "x";
                break;
        }
    }
}