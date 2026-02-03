class ExtensionPointName<T> {
    public static <T> ExtensionPointName<T> create(String s) { return null; }
}

class Foo<B> {
    public static <T> T[] getExtensions(ExtensionPointName<T> extensionPointName) {
      return null;
    }

    ExtensionPointName<B> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");

    void f() {
        final B[] extensions = getExtensions(<caret>XXX.EXTENSION_POINT_NAME);
    }
}
