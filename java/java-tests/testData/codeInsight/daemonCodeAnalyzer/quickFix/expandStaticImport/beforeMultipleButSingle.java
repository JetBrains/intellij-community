// "Replace static import with qualified access to Arrays|->Replace this occurrence and keep the import" "true-preview"
import static java.util.Arrays.*;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        s<caret>ort(destinationAddressNames);
        asList(destinationAddressNames)
    }
}