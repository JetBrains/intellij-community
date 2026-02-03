class Temp {
    public static void main(String... args) {
        if (args.length > 0)
            check<caret>Args(args);
        else
            System.out.println("help");
    }

    private static void checkArgs(String[] args) {
        if (args[0].equals("foo"))
            System.out.println("bar");
    }
}