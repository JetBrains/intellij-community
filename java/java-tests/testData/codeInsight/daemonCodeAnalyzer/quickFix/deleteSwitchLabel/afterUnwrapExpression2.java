// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            //1
            //2
            //3
            //4
            //5
            //6
            //7
            //other
            System.out.println("five-ten-fifteen");
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
