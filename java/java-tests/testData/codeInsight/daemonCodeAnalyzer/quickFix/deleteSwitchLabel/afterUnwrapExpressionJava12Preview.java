// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            System.out.println(switch (x) {
                //1
                //2
                //3
                //4
                case 5 -> {
                    System.out.println("something");
                    break "five-ten-fifteen"; //5
                }
                //6
                //7
                default -> "and more"; //other
            });
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
