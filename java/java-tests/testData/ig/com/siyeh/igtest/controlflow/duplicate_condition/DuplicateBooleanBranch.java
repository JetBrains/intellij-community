package com.siyeh.igtest.controlflow.duplicate_boolean_branch;

public class DuplicateBooleanBranch {

  boolean x(boolean b, boolean c){
    return <warning descr="Duplicate condition 'b'">b</warning> && <warning descr="Duplicate condition 'b'">b</warning> && c;
  }

  boolean npe() {
    return (1 == 1) && (1 ==<error descr="Expression expected"> </error>);
  }
}