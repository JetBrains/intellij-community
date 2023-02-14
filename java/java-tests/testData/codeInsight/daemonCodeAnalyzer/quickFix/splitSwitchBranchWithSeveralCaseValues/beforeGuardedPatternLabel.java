// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(Object o) {
        String s = "";
        switch (o) {
            case ((((Number n)) && ((n.intValue() == 42))))<caret>, null:
                s = "x";
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + o);
    }
  }
}