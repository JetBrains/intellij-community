import org.jetbrains.annotations.Nullable;

public class OutputVariableDuplicate {
    String foo() {
        String var = newMethod();
        if (var == null) return null;
        System.out.println(var);
        return var;
    }

    @Nullable
    private String newMethod() {
        String var = "";
        if (var == null) {
            return null;
        }
        return var;
    }

    String bar() {
        String var = newMethod();
        if (var == null) return null;
        System.out.println(var);
        return var;
    }
}