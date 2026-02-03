import java.io.FileInputStream;

class Launcher<T> {
    {
        try {
            FileInputStream f = new File<caret>InputStream("");
          catch (Exception e) {
            e.printStackTrace();
        }
    }
}