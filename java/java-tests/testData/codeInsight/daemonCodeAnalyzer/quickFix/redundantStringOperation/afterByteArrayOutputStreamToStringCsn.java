// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

class Main {

    private static ByteArrayOutputStream foo() {
        return new ByteArrayOutputStream();
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        String csn = "ISO-8859-1";

        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        String s1 = out1.toString(csn);

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        String s2 = out2.toString((csn));

        ByteArrayOutputStream out3 = new ByteArrayOutputStream();
        String s3 = out3.toString(csn);

        ByteArrayOutputStream out4 = new ByteArrayOutputStream();
        String s4 = out4.toString((csn));

        String s5 = foo().toString(csn);
        String s6 = foo().toString((csn));
    }
}
