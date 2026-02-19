// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

    public static void main(String[] args) {
        Collection unmodifiableCollection = Collections/*empty*/./*empty too*/unmodifiableCollecti<caret>on/*blah blah blah*/(Collections.emptyList());

        List unmodifiableList = Collections/*empty*/./*empty too*/unmodifiableList/*blah blah blah*/(Collections.emptyList());
        Set unmodifiableSet = Collections/*empty*/./*empty too*/unmodifiableSet/*blah blah blah*/(Collections.emptySet());
        Map unmodifiableMap = Collections/*empty*/./*empty too*/unmodifiableMap/*blah blah blah*/(Collections.emptyMap());

        SortedSet unmodifiableSortedSet = Collections/*empty*/./*empty too*/unmodifiableSortedSet/*blah blah blah*/(Collections.emptySet());
        SortedMap unmodifiableSortedMap = Collections/*empty*/./*empty too*/unmodifiableSortedMap/*blah blah blah*/(Collections.emptyMap());

        NavigableSet unmodifiableNavigableSet = Collections/*empty*/./*empty too*/unmodifiableNavigableSet/*blah blah blah*/(Collections.emptySet());
        NavigableMap unmodifiableNavigableMap = Collections/*empty*/./*empty too*/unmodifiableNavigableMap/*blah blah blah*/(Collections.emptyMap());
    }
}
