import static Super.FOO;

class Super {
  public static final Super FOO = null;
  public static Super FOO2() {};
}

class Intermediate {

    Super s = FOO;<caret>
}


