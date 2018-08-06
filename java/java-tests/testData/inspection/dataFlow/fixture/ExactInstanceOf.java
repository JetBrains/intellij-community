class Main {

  public static void main(String args[])
  {
    One one = new One();
    One two = new Two();

    if (<warning descr="Condition 'one instanceof Two' is always 'false'">one instanceof Two</warning>)
    {
      System.out.println(one);
    }

    if (<warning descr="Condition 'two instanceof Two' is always 'true'">two instanceof Two</warning>)
    {
      System.out.println(one);
    }

  }
}

class One { }

class Two extends One { }