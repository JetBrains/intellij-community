// "Replace static import with qualified access to Arrays" "true-preview"
import<caret> static java.util.Arrays.sort;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        sort(destinationAddressNames);
    }
}