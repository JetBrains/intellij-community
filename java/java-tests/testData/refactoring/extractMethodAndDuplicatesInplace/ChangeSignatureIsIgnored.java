class SomeClass {

    public boolean test(String filePath) {
        return <selection>FileUtilRt.extensionEquals(filePath, "jar") ||
               FileUtilRt.extensionEquals(filePath, "zip")</selection>;
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