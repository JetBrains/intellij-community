import static ImportingClass.ImportantConstants.ReallyImportantConstant;

public class ImportingClass {

    public static void main(String[] args) {
        System.out.println(ReallyImportantConstant);
        System.out.println(ImportantConstants.ReallyImportantConstant);
    }

    public static class Constants {
    }

    public static class ImportantConstants {
        public static String ReallyImportantConstant = "important";
    }
}