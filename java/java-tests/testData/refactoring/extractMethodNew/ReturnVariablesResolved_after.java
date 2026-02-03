import org.jetbrains.annotations.Nullable;

class Test {
    String test(){
        String variable = "identifier";
        String variable1 = newMethod(variable);
        if (variable1 != null) return variable1;
        return "return";
    }

    private @Nullable String newMethod(String variable) {
        if (1 == 1) return variable;
        if (2 == 1) return "literal";
        return null;
    }
}