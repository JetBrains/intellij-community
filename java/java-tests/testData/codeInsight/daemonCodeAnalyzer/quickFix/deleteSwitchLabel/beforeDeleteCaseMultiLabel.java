// "Remove switch label '0-/*x*/1'" "true-preview"
class Main {
    static void fff(int x) {
        if (x > 0) {
            switch (x) {
                case 1, 3, 0-<caret>/*x*/1: System.out.println("one"); //1
                case 2: System.out.println("two"); //2
            }
        }
    }
}
