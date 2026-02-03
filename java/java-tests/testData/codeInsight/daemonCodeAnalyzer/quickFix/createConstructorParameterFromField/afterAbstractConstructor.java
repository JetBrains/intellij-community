// "Add constructor parameter" "true"
public enum La {
    ;
    private String s;

    private La(String s) {
        this.s = s;
    }

    private La(int a, String s);
}
