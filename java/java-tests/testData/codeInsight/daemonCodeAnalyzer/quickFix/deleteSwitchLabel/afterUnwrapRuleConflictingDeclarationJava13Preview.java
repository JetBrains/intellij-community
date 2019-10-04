// "Remove unreachable branches" "true"
class Main {
    static void fff(int x) {
        if (x == 5) {
            int i = 5;
            //1
            //2
            //3
            //4
            //6
            //7
            //other
            {
                if (Math.random() > 0.5) return;
                int i = 6;
                System.out.println("five-ten-fifteen"); //5
            }
        }
    }

    public static void main(String[] args) {
        fff();
    }
}
