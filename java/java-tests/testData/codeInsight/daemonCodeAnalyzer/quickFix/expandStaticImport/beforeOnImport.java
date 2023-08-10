// "Replace static import with qualified access to Arrays" "true-preview"
import static java.util.Arrays.sort<caret>;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        sort(destinationAddressNames);
    }
}