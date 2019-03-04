// "Remove unreachable branches" "true"
class Main {
    static void fff() {
        switch ("one") {
            case "<caret>one":
                System.out.println("one");
            case "two":
                System.out.println("two");
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
