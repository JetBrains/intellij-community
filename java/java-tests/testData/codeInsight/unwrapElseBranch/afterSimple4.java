// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    String f(boolean b) {
        if (b)
            System.out.println("When true");
        else
            return "Otherwise";
        return "Default";
    }
}