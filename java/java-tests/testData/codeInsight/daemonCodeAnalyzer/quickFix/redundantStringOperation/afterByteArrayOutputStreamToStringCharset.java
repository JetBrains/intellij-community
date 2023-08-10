// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

class Main {

    private static ByteArrayOutputStream foo() {
      return new ByteArrayOutputStream();
    }

    public static void main(String[] args) {
        Charset charset = Charset.defaultCharset();

        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        String s1 = out1.toString(charset);

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        String s2 = out2.toString((charset));

        ByteArrayOutputStream out3 = new ByteArrayOutputStream();
        String s3 = out3.toString(charset);

        ByteArrayOutputStream out4 = new ByteArrayOutputStream();
        String s4 = out4.toString((charset));

        String s5 = foo().toString(charset);
        String s6 = foo().toString((charset));
    }
}
