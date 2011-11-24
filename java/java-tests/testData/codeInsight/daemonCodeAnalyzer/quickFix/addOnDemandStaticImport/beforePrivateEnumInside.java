// "Add on demand static import for 'test.Outer.State'" "false"
package test;

public class Outer {
    private enum State { REDONE, UNDONE }

    private State state = Sta<caret>te.REDONE;
}