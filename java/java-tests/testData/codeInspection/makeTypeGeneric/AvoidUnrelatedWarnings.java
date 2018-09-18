import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import java.util.stream.Collectors;

class ChangeIteratorTypeFail {

    private static void test(List<String> greeting) {

        final String[] helloBugArray = new String[] { "Hello", ",", " ", "bug", "!" };

        Ite<caret>rator iterator = greeting.iterator();

        while (iterator.hasNext()) {
            String userGreeting = (String) iterator.next();
            System.out.println(userGreeting);

            String bugGreeting = Arrays
                .stream(helloBugArray, 0, helloBugArray.length)
                .collect(Collectors.joining(""));

            System.out.println(bugGreeting);
        }
    }

}