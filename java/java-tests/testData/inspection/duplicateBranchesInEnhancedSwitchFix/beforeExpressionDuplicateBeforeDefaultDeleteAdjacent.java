// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        String s = switch (n) {
            case 2 -> bar("B");
            case 1 -> bar("A");<caret>
            default -> bar("A");
        }
    }
    String bar(String s){return s;}
}