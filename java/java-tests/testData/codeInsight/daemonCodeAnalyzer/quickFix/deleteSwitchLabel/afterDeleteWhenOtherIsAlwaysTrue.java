// "Remove switch label '"two"'" "true"
class Main {
    static void fff() {
        switch ("one") {
            case "one":
                System.out.println("one");
                // quick-fix removes body as well
                System.out.println("two");
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
