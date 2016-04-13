// "Make 'b' return 'void'" "false"

class Test {
    static void a() {}

    static String b() {
        return <caret>a();
    }
}