import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class EffectivelyLocalVariables {
    void test() {
        Scanner inputStreamScanner = null;
        String theFirstLineFromDestinationFile;
        String originContent = "aaa";
        String fileName = "bbb";

        ddd(originContent, fileName);
    }

    private void ddd(String originContent, String fileName) {
        Scanner inputStreamScanner;
        String theFirstLineFromDestinationFile;
        NewMethodResult x = newMethod(fileName, originContent);
    }

    NewMethodResult newMethod(String fileName, String originContent) {
        Scanner inputStreamScanner;
        String theFirstLineFromDestinationFile;
        try {
            inputStreamScanner =
              new Scanner(new File(fileName));
            theFirstLineFromDestinationFile = inputStreamScanner.nextLine();
            assertEquals(theFirstLineFromDestinationFile, originContent);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new NewMethodResult((null /* missing value */), (null /* missing value */));
    }

    static class NewMethodResult {
        private Scanner inputStreamScanner;
        private String theFirstLineFromDestinationFile;

        public NewMethodResult(Scanner inputStreamScanner, String theFirstLineFromDestinationFile) {
            this.inputStreamScanner = inputStreamScanner;
            this.theFirstLineFromDestinationFile = theFirstLineFromDestinationFile;
        }
    }

    void dup() {
        Scanner inputStreamScanner = null;
        String theFirstLineFromDestinationFile;
        String originContent = "";
        String fileName = "";

        try {
            inputStreamScanner =
              new Scanner(
                new File(fileName));
            theFirstLineFromDestinationFile = inputStreamScanner.nextLine();
            // destination should contain original file's content
            assertEquals(theFirstLineFromDestinationFile, originContent);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    private static void assertEquals(Object a, Object b) {}
}