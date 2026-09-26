public class OutputVariableDuplicate {
    String foo() {
        <selection>String var = "";
        if (var == null) {
            return null;
        }</selection>
        System.out.println(var);
        return var;
    }

    String bar() {
        String var = "";
        if (var == null) {
            return null;
        }
        System.out.println(var);
        return var;
    }
}