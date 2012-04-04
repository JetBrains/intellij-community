class RenameTest {
    static void fo<caret>o1(Number n) {
        System.out.println("1");
    }
    static void foo2(Long i) {
        System.out.println("2");
    }
    public static void main(String[] args) {
        long n = 0;
        foo1(n);
    }
}