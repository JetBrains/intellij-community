// "Expand static import to Arrays.sort" "true"
import static java.util.Arrays.*;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        s<caret>ort(destinationAddressNames);
        asList(destinationAddressNames)
    }
}