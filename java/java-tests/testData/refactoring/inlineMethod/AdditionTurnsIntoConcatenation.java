class AdditionTurnsIntoConcatenation {

    public static void main(String[] args) throws Exception {
        String s = "1" + m<caret>() + "2";
        System.out.println(s);
    }

    private static String m() {
        return 1 + 2 + "x";
    }
}