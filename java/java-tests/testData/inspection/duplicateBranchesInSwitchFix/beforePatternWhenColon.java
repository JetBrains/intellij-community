// "Merge with 'case String _'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(Object obj) {
        switch (obj) {
            case String _ when !((String)obj).isEmpty():
                System.out.println("x");
                break;
            case Integer _ when ((Integer)obj) > 0:
                <caret>System.out.println("x");
                break;
            case Double d when d > 0:
                System.out.println("x");
                break;
            default:
        }
        return "";
    }
}