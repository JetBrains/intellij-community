// "Replace static import with qualified access to Arrays" "true-preview"
import static java.util.Array<caret>s.*;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        sort(destinationAddressNames);
    }
}