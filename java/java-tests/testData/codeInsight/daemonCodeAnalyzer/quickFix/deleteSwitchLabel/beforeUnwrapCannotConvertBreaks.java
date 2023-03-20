// "Remove unreachable branches" "true-preview"
class Main {
    static void fff(int x) {
        if (x == 5) {
            switch (x) {
                case 1: System.out.println("one"); //1
                case 2: System.out.println("two"); //2
                case 3: System.out.println("three"); //3
                case 4: System.out.println("four"); //4
                case 0:case <caret>5:case 10:
                    if (Math.random() > 0.5) break;
                    System.out.println("five-ten-fifteen"); //5
                case 6: System.out.println("six"); //6
                case 7: System.out.println("seven"); //7
                    break;
                default: System.out.println("and more"); //other
            }
            System.out.println("oops");
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
