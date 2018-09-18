// "Replace static import with qualified access to Arrays" "true"
import static java.util.Arrays.so<caret>rt;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        sort(destinationAddressNames);
    }
}