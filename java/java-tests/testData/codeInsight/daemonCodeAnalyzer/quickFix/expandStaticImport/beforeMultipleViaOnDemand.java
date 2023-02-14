// "Replace static import with qualified access to Arrays" "true-preview"
import static java.util.Arrays.*;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        s<caret>ort(destinationAddressNames);
        asList(destinationAddressNames)
    }
}