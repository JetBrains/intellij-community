class SomeClass {

    public boolean test(String filePath) {
        return isABoolean(filePath);
    }

    private static boolean isABoolean(String filePath) {
        return FileUtilRt.extensionEquals(filePath, "jar") ||
                FileUtilRt.extensionEquals(filePath, "zip");
    }

    public void test2(String s){
        String parent = s != null ? s.substring(4) : null;
    }
}

class FileUtilRt {
    static boolean extensionEquals(String filename, String extension) {
        return true;
    }
}