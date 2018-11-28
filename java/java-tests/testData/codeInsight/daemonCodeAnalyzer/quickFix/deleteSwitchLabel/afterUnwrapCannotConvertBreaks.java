// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            switch (x) {
                //1
                //2
                //3
                //4
                case 5:
                    if (Math.random() > 0.5) break;
                    System.out.println("five-ten-fifteen"); //5
                    System.out.println("six"); //6
                    System.out.println("seven"); //7
                    break;
                //other
            }
            System.out.println("oops");
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
