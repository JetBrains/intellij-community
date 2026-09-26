class ElseIf {
    String foo(boolean a, boolean b) {
        if (a) {

        } else <selection>if (b) {
            String s = bar();
            if (s != null) return s;
        }</selection>

        return null;
    }

    String bar() { return "";}
}
