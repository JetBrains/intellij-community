// "Unwrap 'if' statement" "true"
class X {
    public String testUnwrap(String f) {
        switch (f) {
            case "A":
                return "A";
            case "B":
                return "b";
            case "D":
                return "D";
            default:
                return null;
        }
    }
}