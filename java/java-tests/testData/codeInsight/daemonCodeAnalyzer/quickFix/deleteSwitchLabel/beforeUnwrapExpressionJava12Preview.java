// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            System.out.println(switch (x) {
                case 1 -> "one"; //1
                case 2 -> "two"; //2
                case 3 -> "three"; //3
                case 4 -> "four"; //4
                case 0, <caret>5, 10 -> {
                    System.out.println("something");
                    break "five-ten-fifteen"; //5
                }
                case 6 -> "six"; //6
                case 7 -> "seven"; //7
                default -> "and more"; //other
            });
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
