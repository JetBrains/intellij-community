// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;

class Main {

    public static void main(String[] args) {
        Collections/*empty*/./*empty too*/unmodifiableCollecti<caret>on/*blah blah blah*/(Collections.emptyList());

        Collections/*empty*/./*empty too*/unmodifiableList/*blah blah blah*/(Collections.emptyList());
        Collections/*empty*/./*empty too*/unmodifiableSet/*blah blah blah*/(Collections.emptySet());
        Collections/*empty*/./*empty too*/unmodifiableMap/*blah blah blah*/(Collections.emptyMap());

        Collections/*empty*/./*empty too*/unmodifiableSortedSet/*blah blah blah*/(Collections.emptySet());
        Collections/*empty*/./*empty too*/unmodifiableSortedMap/*blah blah blah*/(Collections.emptyMap());

        Collections/*empty*/./*empty too*/unmodifiableNavigableSet/*blah blah blah*/(Collections.emptySet());
        Collections/*empty*/./*empty too*/unmodifiableNavigableMap/*blah blah blah*/(Collections.emptyMap());
    }
}
