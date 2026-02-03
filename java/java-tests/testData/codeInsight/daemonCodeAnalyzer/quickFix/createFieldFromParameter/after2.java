// "Create field for parameter 'test'" "true-preview"

package codeInsight.createFieldFromParameterAction.test1;

import java.util.HashMap;

public class TestBefore {
    private String myName;
    private final HashMap <caret>myTest;
    private String myPerson;

    public TestBefore(String name, int length, HashMap test, String person) {
        super();
        myName = name;
        myTest = test;
        myPerson = person;
    }
}
