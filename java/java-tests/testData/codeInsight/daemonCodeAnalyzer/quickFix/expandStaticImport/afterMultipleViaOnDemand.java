// "Replace static import with qualified access to Arrays|->Replace all and delete the import" "true-preview"
import java.util.Arrays;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        Arrays.sort(destinationAddressNames);
        Arrays.asList(destinationAddressNames)
    }
}