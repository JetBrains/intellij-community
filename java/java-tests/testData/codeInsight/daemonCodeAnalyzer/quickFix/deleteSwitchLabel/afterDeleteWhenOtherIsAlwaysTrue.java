// "Remove unreachable branches" "true-preview"
class Main {
    static void fff() {
        System.out.println("one");
        System.out.println("two");
    }

    public static void main(String[] args) {
        fff();
    }
}
