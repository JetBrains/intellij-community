// "Create Field For Parameter 'length'" "true"

package codeInsight.createFieldFromParameterAction.test1;

import java.util.HashMap;

public class TestBefore {
    private String myName;
    private final int myLength;
    private final HashMap myTest;
    private String myPerson;

    public TestBefore(String name, int length, HashMap test, String person) {
        super();
        myName = name;
        myLength = length;
        myTest = test;
        myPerson = person;
    }
}
