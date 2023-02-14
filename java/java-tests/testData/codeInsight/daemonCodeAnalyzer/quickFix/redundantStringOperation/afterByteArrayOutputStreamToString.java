// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.io.ByteArrayOutputStream;

class Main {

    private static ByteArrayOutputStream foo() {
        return new ByteArrayOutputStream();
    }

    public static void main(String[] args) {
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        String s1 = out1.toString();

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        String s2 = out2.toString();

        ByteArrayOutputStream out3 = new ByteArrayOutputStream();
        String s3 = out3.toString();

        ByteArrayOutputStream out4 = new ByteArrayOutputStream();
        String s4 = out4.toString();

        String s5 = foo().toString();
        String s6 = foo().toString();
  }
}
