// "Replace static import with qualified access to Arrays" "true"
import java.util.Arrays;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        Arrays.sort(destinationAddressNames);
        Arrays.asList(destinationAddressNames)
    }
}