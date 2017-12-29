import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

class C {
    void m(File file) throws IOException {
        <caret>FileInputStream fileInputStream = new FileInputStream(file);//comment
        int read, len = -1;
        boolean empty = true;
        do {
            read = fileInputStream.read();
            char[] chars = Character.toChars(read);
            System.out.println(read + " = " + Arrays.toString(chars));
            ++len;
            empty = false;
        }
        while (read != -1);
        System.out.println(len);
        System.out.println(empty);
    }
}
