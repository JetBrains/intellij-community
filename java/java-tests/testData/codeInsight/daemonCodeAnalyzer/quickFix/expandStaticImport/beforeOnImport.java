// "Expand static import to Arrays.sort" "true"
import static java.util.Arrays.so<caret>rt;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        sort(destinationAddressNames);
    }
}