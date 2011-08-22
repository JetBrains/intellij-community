// "Expand static import to Arrays.sort" "true"
import java.util.Arrays;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        Arrays.sort(destinationAddressNames);
        Arrays.asList(destinationAddressNames)
    }
}