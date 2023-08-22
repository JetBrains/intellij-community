// "Replace static import with qualified access to Arrays|->Replace this occurrence and keep the import" "true-preview"
import java.util.Arrays;

import static java.util.Arrays.*;

class Test {
    public void sendMessage(String... destinationAddressNames) {
        Arrays.sort(destinationAddressNames);
        asList(destinationAddressNames)
    }
}