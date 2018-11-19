// "Remove switch branch '-/*x*/1'" "true"
class Main {
    static void fff(int x) {
        if (x > 0) {
            switch (x) {
                case 2 -> System.out.println("two"); //2
                case -<caret>/*x*/1 -> System.out.println("one"); //1
            }
        }
    }
}
