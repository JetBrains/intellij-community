public class Test {
    public static void main(String[] args) {
        //noinspection SillyAssignment
        args = args;
        int i = 0;
        if (i == 0) {

        } else //noinspection SillyAssignment
          <error descr="Not a statement">i == i;</error>
    }
}
