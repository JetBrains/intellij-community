class Main {
    void f(Throwable t){
        main("", <caret>t);
    }

    public static void main(String s, Throwable t, String... args) { }

    public static void main(String s,  String... args) { }
}