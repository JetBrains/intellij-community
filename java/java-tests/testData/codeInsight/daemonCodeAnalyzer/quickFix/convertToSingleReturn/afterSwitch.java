// "Transform body to single exit-point form" "true"
class Test {
    String test(int x) {
        String result = "foo";
        switch (x) {
            case 1:
                break;
            case 2:
                result = "bar";
                break;
            case 3:
                result = "baz";
                break;
            default:
                result = "qux";
                break;
        }
        return result;
    }
}