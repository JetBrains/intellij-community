class Super {
}

class Sub extends Super {
  public static final Super FOO = null;
}

class Intermediate {

    Super s = Sub.FOO;<caret>
}


