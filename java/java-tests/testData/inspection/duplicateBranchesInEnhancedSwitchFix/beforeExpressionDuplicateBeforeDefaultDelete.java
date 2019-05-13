// "Delete redundant 'switch' result expression" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        String s = switch (n) {
            case 1 -> bar("A");<caret>
            case 2 -> bar("B");
            default -> bar("A");
        }
    }
    String bar(String s){return s;}
}