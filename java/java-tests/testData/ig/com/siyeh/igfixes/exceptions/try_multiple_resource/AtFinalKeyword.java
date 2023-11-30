import java.io.*;
class AtFinalKeyword {
    void foo(File file1, File file2) throws IOException {
        try (f<caret>inal FileInputStream in = /*comment in first*/new FileInputStream(file1);//after first resource var
        FileOutputStream out /*comment in out*/= new FileOutputStream(file2)) {//comment
            System.out.println(in + ", " /*inside statement*/+ out); //commnt at sout
        }//after end
    }
}