import static java.util.Arrays.toString;

class ImportDuty {
    public static void main(String[] args) {
        new ImportDuty().printArgs(1, 2, 3, 4, 5);
    }

    void printArgs(Object... args) {
        System.out.println(<ref>toString(args));
    }
}
