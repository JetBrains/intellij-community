// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            //1
            //2
            //3
            //4
            System.out.println("five-ten-fifteen"); //5
            System.out.println("six"); //6
            System.out.println("seven"); //7
            //other
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
