import java.io.FileOutputStream;

class Test {
    static void foo() throws Exception {
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream("c:\\myfile");
        } catch (Exception ex) {
            throw new Exception("Oh, dear me.", ex);
        } finally {
            if (fos != null) {
            }
        }
    }
}
