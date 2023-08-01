package com.siyeh.igtest.assignment.assignment_used_as_condition;

public class AssignmentUsedAsCondition {

  void foo(boolean b) {
    if (<warning descr="Assignment 'b = fossa()' used as condition">b = fossa()</warning>) {
    }
    if ((<warning descr="Assignment 'b = fossa()' used as condition">b = fossa()</warning>)) {}
  }

  boolean fossa() {
    return true;
  }

  public static void main(String[] args) {
    boolean b = false;
    if (b !=<error descr="Expression expected"> </error>= true) {

    }
    while (<warning descr="Assignment '((b)) = true' used as condition">((b)) = true</warning>) {}
    int i = 1;
    if (<error descr="Incompatible types. Found: 'int', required: 'boolean'">i = 8</error>);
    if (b &= true);
  }

  void array() {
    boolean[] arr = {true};
    if (<warning descr="Assignment 'arr[0] = false' used as condition">arr[0] = false</warning>) {

    }
  }
}